package com.rotarypumpers.meraki.core;
import java.io.*;import java.nio.file.*;import java.nio.file.attribute.BasicFileAttributes;
/** Deletes only the two obsolete staging directories; no symlink traversal. */
public final class LegacyShareCleanup {
 public static void run(File files,File cache)throws IOException{remove(files.toPath().resolve("visualizer-payloads"));remove(cache.toPath().resolve("bean-transfers"));}
 private static void remove(Path root)throws IOException{if(!Files.exists(root,LinkOption.NOFOLLOW_LINKS))return;Files.walkFileTree(root,new SimpleFileVisitor<Path>(){
  @Override public FileVisitResult visitFile(Path f,BasicFileAttributes a)throws IOException{Files.delete(f);return FileVisitResult.CONTINUE;}
  @Override public FileVisitResult postVisitDirectory(Path dir,IOException e)throws IOException{if(e!=null)throw e;Files.delete(dir);return FileVisitResult.CONTINUE;}
 });}
}
