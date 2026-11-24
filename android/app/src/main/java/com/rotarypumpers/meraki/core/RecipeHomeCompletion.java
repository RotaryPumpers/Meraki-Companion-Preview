package com.rotarypumpers.meraki.core;
/** Cosmetic stage cannot run on partial recipes or resume any settings command. */
public final class RecipeHomeCompletion{
 public interface Apply{void run()throws Exception;}
 public static boolean apply(RecipeControls.Progress progress,Apply action)throws Exception{if(!progress.complete)return false;action.run();return true;}
 private RecipeHomeCompletion(){}
}
