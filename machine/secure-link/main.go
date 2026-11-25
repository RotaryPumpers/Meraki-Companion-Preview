// Secure Link is an experimental, foreground, read-only recorder transport.
package main

import (
	"bufio"
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"errors"
	"flag"
	"fmt"
	"io"
	"math/big"
	"net"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"regexp"
	"runtime"
	"runtime/debug"
	"strconv"
	"strings"
	"syscall"
	"time"
)

const version = "0.1.2-lab"
// Set at build time to pair this listener with a locally built recorder.
var recorderSHA = "b2acd43a1d0c3e40cc57004b3a4fcfd54b3171dee1d47790fcb1ea3bd99f6730"

var ackPattern = regexp.MustCompile(`^ACK (0|[1-9][0-9]{0,19})$`)

type credentials struct {
	Certificate string `json:"certificate_pem"`
	PrivateKey  string `json:"private_key_pem"`
	Token       string `json:"token"`
}
type pairing struct {
	Protocol          string `json:"protocol"`
	CertificateSHA256 string `json:"certificate_sha256"`
	Token             string `json:"token"`
	Port              int    `json:"port"`
}

func createCredentials() (credentials, pairing, error) {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return credentials{}, pairing{}, err
	}
	serial, err := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 128))
	if err != nil {
		return credentials{}, pairing{}, err
	}
	template := x509.Certificate{SerialNumber: serial, Subject: pkix.Name{CommonName: "Rotary Pumpers Secure Link"}, NotBefore: time.Date(2020, 1, 1, 0, 0, 0, 0, time.UTC), NotAfter: time.Date(2040, 1, 1, 0, 0, 0, 0, time.UTC), KeyUsage: x509.KeyUsageDigitalSignature, ExtKeyUsage: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth}}
	der, err := x509.CreateCertificate(rand.Reader, &template, &template, &key.PublicKey, key)
	if err != nil {
		return credentials{}, pairing{}, err
	}
	pk, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		return credentials{}, pairing{}, err
	}
	token := make([]byte, 32)
	if _, err = rand.Read(token); err != nil {
		return credentials{}, pairing{}, err
	}
	h := sha256.Sum256(der)
	c := credentials{string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der})), string(pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: pk})), hex.EncodeToString(token)}
	return c, pairing{"rp-secure-stream-v1", hex.EncodeToString(h[:]), c.Token, 9443}, nil
}
func newTLS(c credentials) (*tls.Config, error) {
	token, err := hex.DecodeString(c.Token)
	if err != nil || len(token) != 32 {
		return nil, errors.New("invalid pairing token")
	}
	cert, err := tls.X509KeyPair([]byte(c.Certificate), []byte(c.PrivateKey))
	if err != nil {
		return nil, err
	}
	return &tls.Config{Certificates: []tls.Certificate{cert}, MinVersion: tls.VersionTLS12, MaxVersion: tls.VersionTLS13, SessionTicketsDisabled: true}, nil
}
func readBounded(r *bufio.Reader, limit int) (string, error) {
	var out []byte
	for len(out) <= limit {
		b, err := r.ReadByte()
		if err != nil {
			return "", err
		}
		if b == '\n' {
			return string(out), nil
		}
		out = append(out, b)
	}
	return "", errors.New("protocol line too long")
}
func allowedIP(ip net.IP) bool {
	return ip != nil && ip.To4() != nil && (ip.IsPrivate() || ip.IsLoopback())
}
func verifyRecorder(path string) error {
	info, err := os.Lstat(path)
	if err != nil {
		return err
	}
	if !info.Mode().IsRegular() || info.Mode().Perm()&0022 != 0 {
		return errors.New("recorder must be a regular file not writable by group/others")
	}
	b, err := os.ReadFile(path)
	if err != nil {
		return err
	}
	h := sha256.Sum256(b)
	if hex.EncodeToString(h[:]) != recorderSHA {
		return errors.New("recorder checksum mismatch")
	}
	return nil
}
func displayPID() (string, error) { return displayPIDAt("/proc") }

func displayPIDAt(procRoot string) (string, error) {
	entries, err := os.ReadDir(procRoot)
	if err != nil {
		return "", err
	}
	found := ""
	for _, e := range entries {
		if _, err := strconv.Atoi(e.Name()); err != nil {
			continue
		}
		exe, err := os.Readlink(filepath.Join(procRoot, e.Name(), "exe"))
		// Thread names are mutable: the real machine reports audio_play.
		// The native recorder independently verifies firmware hash and identity.
		if err == nil && (exe == "/new_customer/demo_v9" || exe == "/customer/demo_v9") {
			if found != "" {
				return "", fmt.Errorf("multiple display processes: %s and %s", found, e.Name())
			}
			found = e.Name()
		}
	}
	if found == "" {
		return "", errors.New("display process not found")
	}
	return found, nil
}

type runner func(context.Context, io.Reader, io.Writer) error

func nativeRunner(path string) runner { return nativeRunnerForPID(path, "") }

func verifiedPIDAt(root, pid string) (string, error) {
	n, err := strconv.ParseUint(pid, 10, 31)
	if err != nil || n == 0 || strconv.FormatUint(n, 10) != pid {
		return "", errors.New("PID must be a positive canonical decimal integer")
	}
	exe, err := os.Readlink(filepath.Join(root, pid, "exe"))
	if err != nil {
		return "", fmt.Errorf("cannot inspect selected PID %s: %w", pid, err)
	}
	if exe != "/customer/demo_v9" && exe != "/new_customer/demo_v9" {
		return "", fmt.Errorf("selected PID %s is not a supported display executable", pid)
	}
	return pid, nil
}

func nativeRunnerForPID(path, selectedPID string) runner {
	return func(ctx context.Context, in io.Reader, out io.Writer) error {
		if err := verifyRecorder(path); err != nil {
			return err
		}
		var pid string
		var err error
		if selectedPID != "" {
			pid, err = verifiedPIDAt("/proc", selectedPID)
		} else {
			pid, err = displayPID()
		}
		if err != nil {
			return err
		}
		// Fixed executable and argv only. No shell, caller-supplied PID or commands.
		cmd := exec.CommandContext(ctx, path, "--stream", pid)
		cmd.Stdin = in
		cmd.Stdout = out
		cmd.Stderr = out
		cmd.WaitDelay = 2 * time.Second
		return cmd.Run()
	}
}

type deadlineWriter struct{ net.Conn }

func (w deadlineWriter) Write(p []byte) (int, error) {
	if err := w.SetWriteDeadline(time.Now().Add(15 * time.Second)); err != nil {
		return 0, err
	}
	return w.Conn.Write(p)
}
func handle(parent context.Context, raw net.Conn, conf *tls.Config, token string, run runner) error {
	defer raw.Close()
	conn := tls.Server(raw, conf)
	defer conn.Close()
	if err := conn.SetDeadline(time.Now().Add(10 * time.Second)); err != nil {
		return err
	}
	if err := conn.HandshakeContext(parent); err != nil {
		return err
	}
	reader := bufio.NewReaderSize(conn, 4096)
	line, err := readBounded(reader, 80)
	if err != nil {
		return err
	}
	supplied := strings.TrimPrefix(line, "RP/1 ")
	if !strings.HasPrefix(line, "RP/1 ") || len(supplied) != 64 || subtle.ConstantTimeCompare([]byte(supplied), []byte(token)) != 1 {
		return errors.New("pairing rejected")
	}
	if err := conn.SetDeadline(time.Time{}); err != nil {
		return err
	}
	ctx, cancel := context.WithTimeout(parent, 2*time.Hour+30*time.Second)
	defer cancel()
	input, output := io.Pipe()
	defer input.Close()
	defer output.Close()
	// A network failure immediately cancels the child. Strict ACK grammar only.
	done := make(chan struct{})
	go func() {
		defer close(done)
		defer cancel()
		defer output.Close()
		for {
			_ = conn.SetReadDeadline(time.Now().Add(20 * time.Second))
			line, err := readBounded(reader, 64)
			if err != nil || !ackPattern.MatchString(line) {
				return
			}
			if _, err = strconv.ParseUint(strings.TrimPrefix(line, "ACK "), 10, 64); err != nil {
				return
			}
			if _, err = io.WriteString(output, line+"\n"); err != nil {
				return
			}
		}
	}()
	watcher := make(chan struct{})
	defer close(watcher)
	go func() {
		select {
		case <-ctx.Done():
			conn.Close()
			input.Close()
			output.Close()
		case <-watcher:
		}
	}()
	err = run(ctx, input, deadlineWriter{conn})
	cancel()
	conn.Close()
	input.Close()
	output.Close()
	<-done
	return err
}
func initialize(dir string) error {
	// Refuse to overwrite pairing credentials. No startup scripts are touched.
	if err := os.Mkdir(dir, 0700); err != nil {
		return err
	}
	c, p, err := createCredentials()
	if err != nil {
		return err
	}
	for name, value := range map[string]any{"device-private.json": c, "phone-pairing.json": p} {
		b, err := json.MarshalIndent(value, "", "  ")
		if err != nil {
			return err
		}
		if err = os.WriteFile(filepath.Join(dir, name), append(b, '\n'), 0600); err != nil {
			return err
		}
	}
	return nil
}
func main() {
	runtime.GOMAXPROCS(1)
	debug.SetMemoryLimit(16 << 20)
	debug.SetMaxThreads(32)
	initDir := flag.String("init", "", "create new local credential directory, no network")
	listen := flag.String("listen", "", "specific private IPv4:9443; no wildcard binding")
	config := flag.String("config", "", "local device-private.json")
	recorder := flag.String("recorder", "", "local verified rp-capture-armv7")
	selectedPID := flag.String("pid", "", "optional locally selected display PID; never accepted over the network")
	self := flag.Bool("self-test", false, "cryptographic configuration test; no machine access")
	ver := flag.Bool("version", false, "print version")
	flag.Parse()
	if *ver {
		fmt.Println("RP_SECURE_LINK", version)
		return
	}
	if *self {
		c, _, err := createCredentials()
		if err == nil {
			_, err = newTLS(c)
		}
		if err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
		fmt.Println("RP_SECURE_LINK_SELF_TEST_OK target=none sockets=none")
		return
	}
	if *initDir != "" {
		if err := initialize(*initDir); err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
		fmt.Println("Pairing files created locally. Keep device-private.json on the device; phone-pairing.json is also secret.")
		return
	}
	host, port, err := net.SplitHostPort(*listen)
	if err != nil || !allowedIP(net.ParseIP(host)) || port != "9443" {
		fmt.Fprintln(os.Stderr, "Use a specific private IPv4 address on port9443")
		os.Exit(2)
	}
	info, err := os.Lstat(*config)
	if err != nil || !info.Mode().IsRegular() || info.Mode().Perm()&0077 != 0 {
		fmt.Fprintln(os.Stderr, "Credentials require a regular owner-only file")
		os.Exit(2)
	}
	b, err := os.ReadFile(*config)
	var c credentials
	if err == nil {
		err = json.Unmarshal(b, &c)
	}
	if err != nil {
		fmt.Fprintln(os.Stderr, "Cannot read credentials")
		os.Exit(2)
	}
	conf, err := newTLS(c)
	if err != nil {
		fmt.Fprintln(os.Stderr, "Invalid credentials")
		os.Exit(2)
	}
	path, err := filepath.Abs(*recorder)
	if err == nil {
		err = verifyRecorder(path)
	}
	if err != nil {
		fmt.Fprintln(os.Stderr, "Recorder verification failed:", err)
		os.Exit(2)
	}
	if *selectedPID != "" {
		if _, err := verifiedPIDAt("/proc", *selectedPID); err != nil {
			fmt.Fprintln(os.Stderr, "Display selection failed:", err)
			os.Exit(2)
		}
	}
	listener, err := net.Listen("tcp4", *listen)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(2)
	}
	defer listener.Close()
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	go func() { <-ctx.Done(); listener.Close() }()
	fmt.Fprintln(os.Stderr, "RP_SECURE_LINK_LISTENING", *listen, "foreground=yes authenticated_tls=yes")
	for {
		conn, err := listener.Accept()
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			continue
		}
		peer, _, _ := net.SplitHostPort(conn.RemoteAddr().String())
		if !allowedIP(net.ParseIP(peer)) {
			conn.Close()
			continue
		}
		// One connection at a time bounds resource use; authentication has a 10s deadline.
		err = handle(ctx, conn, conf, c.Token, nativeRunnerForPID(path, *selectedPID))
		if err != nil {
			fmt.Fprintln(os.Stderr, "RP_SECURE_LINK_SESSION_ENDED reason:", err)
		}
	}
}
