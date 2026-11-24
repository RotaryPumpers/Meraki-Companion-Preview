package com.rotarypumpers.meraki.core;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** App-owned files only. Journal backup remains a separate operation. */
public final class LogArchive {
    public static List<File> select(File directory) throws IOException {
        File[] files=directory.listFiles();
        if(files==null)throw new IOException("Recording folder unavailable");
        List<File> selected=new ArrayList<>();
        for(File f:files){
            String name=f.getName();
            if(f.isFile()&&!Files.isSymbolicLink(f.toPath())&&((name.equals("diagnostics.jsonl")||name.equals("grinder-events.jsonl"))||name.matches("session-[0-9]+\\.jsonl")))selected.add(f);
        }
        selected.sort(Comparator.comparing(File::getName));
        return selected;
    }
    public static void write(File directory,OutputStream output)throws IOException {
        List<File> selected=select(directory);
        if(selected.isEmpty())throw new IOException("No recording logs yet");
        try(ZipOutputStream zip=new ZipOutputStream(output)){
            byte[] buffer=new byte[16384];
            for(File file:selected){
                zip.putNextEntry(new ZipEntry(file.getName()));
                try(InputStream in=new FileInputStream(file)){int n;while((n=in.read(buffer))!=-1)zip.write(buffer,0,n);}
                zip.closeEntry();
            }
        }
    }
}
