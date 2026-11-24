package com.rotarypumpers.meraki.core;
import java.io.IOException;
/** A paired listener closed before validating stream metadata: startup cause not yet known. */
public final class SecureLinkSetupException extends IOException {
 public SecureLinkSetupException(){super("The secure recording connection closed before recording began. If reconnecting fails again, export recording logs for review.");}
}
