package com.villageclock.app;

import android.app.AlertDialog;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import java.util.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk={26,29,35})
public class PlannerUiTest {
    private PlannerActivity sample(){return Robolectric.buildActivity(PlannerActivity.class,new Intent(RuntimeEnvironment.getApplication(),PlannerActivity.class).putExtra("sample",true)).setup().get();}
    private List<UpgradePlanner.Row> rows(PlannerActivity activity){List<UpgradePlanner.Row> rows=new ArrayList<>();ListAdapter adapter=((ListView)activity.findViewById(R.id.planner_list)).getAdapter();for(int n=0;n<adapter.getCount();n++)if(adapter.getItem(n) instanceof UpgradePlanner.Row)rows.add((UpgradePlanner.Row)adapter.getItem(n));return rows;}
    static View find(View view,String text){if(view instanceof Button&&((Button)view).getText().toString().equals(text)||text.equals(String.valueOf(view.getContentDescription())))return view;if(view instanceof ViewGroup){ViewGroup parent=(ViewGroup)view;for(int n=0;n<parent.getChildCount();n++){View found=find(parent.getChildAt(n),text);if(found!=null)return found;}}return null;}
    private AlertDialog filters(PlannerActivity activity){activity.findViewById(R.id.planner_filters).performClick();return ShadowAlertDialog.getLatestAlertDialog();}
    private void click(AlertDialog dialog,String text){View view=find(dialog.getWindow().getDecorView(),text);assertNotNull(text,view);view.performClick();}
    private void apply(AlertDialog dialog){dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(android.os.Looper.getMainLooper()).idle();}
    @Test public void defaultMenuShowsRunningFirstAndOnlyCurrentHallTargets(){
        PlannerActivity activity=sample();List<UpgradePlanner.Row> rows=rows(activity);assertEquals(UpgradePlanner.State.RUNNING,rows.get(0).state);
        assertTrue(((TextView)activity.findViewById(R.id.planner_scope)).getText().toString().contains("Town Hall 15"));
        boolean hall=false;for(UpgradePlanner.Row row:rows){assertTrue(row.withinCurrentHall);if(row.item.id==1000001){hall=true;assertEquals(16,row.targetLevel());}}assertTrue(hall);
    }
    @Test public void buttonsApplyResourceTypeAndDescendingCostsWithoutChangingOtherTabs(){
        PlannerActivity activity=sample();AlertDialog dialog=filters(activity);click(dialog,"Gold");click(dialog,"Defense");click(dialog,"Cost Max");apply(dialog);
        long previous=Long.MAX_VALUE;for(UpgradePlanner.Row row:rows(activity)){assertEquals("Defense",row.item.type());assertTrue(row.costs().keySet().stream().anyMatch(k->k.contains("Gold")));if(!row.active()){assertTrue(row.price(Collections.singleton("Gold"))<=previous);previous=row.price(Collections.singleton("Gold"));}}
        find(activity.getWindow().getDecorView(),"Army Upgrades").performClick();assertFalse(rows(activity).isEmpty());
        find(activity.getWindow().getDecorView(),"Builder Base").performClick();assertTrue(((TextView)activity.findViewById(R.id.planner_scope)).getText().toString().contains("Builder Hall 8"));
        AlertDialog builder=filters(activity);assertNull(find(builder.getWindow().getDecorView(),"Resource"));assertNotNull(find(builder.getWindow().getDecorView(),"Cost Min"));
    }
    @Test public void cancelDiscardsChoicesAndCatalogMaxIsExplicit()throws Exception{
        Context context=RuntimeEnvironment.getApplication();String raw="{\"tag\":\"#SCOPE\",\"timestamp\":"+System.currentTimeMillis()/1000+",\"buildings\":[{\"data\":1000001,\"lvl\":12},{\"data\":1000008,\"lvl\":17}]}";Village village=ExportParser.parse(raw,Store.catalog(context),System.currentTimeMillis());Store.save(context,raw,village);
        PlannerActivity activity=Robolectric.buildActivity(PlannerActivity.class,new Intent(context,PlannerActivity.class).putExtra("tag",village.tag)).setup().get();int before=rows(activity).size();AlertDialog dialog=filters(activity);((Switch)dialog.findViewById(R.id.planner_current_hall)).setChecked(false);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(before,rows(activity).size());
        dialog=filters(activity);((Switch)dialog.findViewById(R.id.planner_current_hall)).setChecked(false);apply(dialog);assertTrue(rows(activity).size()>before);assertTrue(((TextView)activity.findViewById(R.id.planner_scope)).getText().toString().contains("Catalog Max"));
    }
    @Test public void emptyFilterIsNotSilentlyCleared(){
        PlannerActivity activity=sample();AlertDialog dialog=filters(activity);click(dialog,"Dark Elixir");click(dialog,"Resource");apply(dialog);assertTrue(rows(activity).isEmpty());ListView emptyList=activity.findViewById(R.id.planner_list);boolean reset=false;for(int n=0;n<emptyList.getAdapter().getCount();n++)if(emptyList.getAdapter().getItem(n)==null)reset=find(emptyList.getAdapter().getView(n,null,emptyList),"Reset Filters")!=null;assertTrue(reset);
        AlertDialog again=filters(activity);assertTrue(find(again.getWindow().getDecorView(),"Dark Elixir selected").isSelected());
    }
    @Test public void filterScopeSurvivesActivityRecreation(){
        Intent intent=new Intent(RuntimeEnvironment.getApplication(),PlannerActivity.class).putExtra("sample",true);ActivityController<PlannerActivity> controller=Robolectric.buildActivity(PlannerActivity.class,intent).setup();PlannerActivity activity=controller.get();AlertDialog dialog=filters(activity);((Switch)dialog.findViewById(R.id.planner_current_hall)).setChecked(false);apply(dialog);
        Bundle state=new Bundle();controller.saveInstanceState(state).pause().stop().destroy();PlannerActivity restored=Robolectric.buildActivity(PlannerActivity.class,intent).create(state).start().resume().visible().get();assertTrue(((TextView)restored.findViewById(R.id.planner_scope)).getText().toString().contains("Catalog Max"));
    }
    @Test public void cardUsesTargetLevelArtWhileKeepingCurrentLevelBadge(){
        PlannerActivity activity=sample();ListView list=activity.findViewById(R.id.planner_list);ListAdapter adapter=list.getAdapter();
        for(int n=0;n<adapter.getCount();n++)if(adapter.getItem(n) instanceof UpgradePlanner.Row){UpgradePlanner.Row row=(UpgradePlanner.Row)adapter.getItem(n);if(row.item.id!=1000008)continue;View card=adapter.getView(n,null,list);ImageView image=card.findViewById(R.id.planner_item_icon);assertEquals(row.targetLevel(),image.getTag());assertEquals(ItemArt.icon(activity,row.item.id,row.targetLevel()),shadowOf(image.getDrawable()).getCreatedFromResId());assertEquals(19,row.level);return;}fail("No running cannon");
    }
    @Test public void lowHallPetsShowUnlockPanelInsteadOfFutureList()throws Exception{
        Context context=RuntimeEnvironment.getApplication();String raw="{\"tag\":\"#LOW\",\"timestamp\":"+System.currentTimeMillis()/1000+",\"buildings\":[{\"data\":1000001,\"lvl\":12}]}";Village village=ExportParser.parse(raw,Store.catalog(context),System.currentTimeMillis());Store.save(context,raw,village);
        PlannerActivity activity=Robolectric.buildActivity(PlannerActivity.class,new Intent(context,PlannerActivity.class).putExtra("tag",village.tag)).setup().get();find(activity.getWindow().getDecorView(),"Hero Pets").performClick();assertTrue(rows(activity).isEmpty());
        ListView list=activity.findViewById(R.id.planner_list);View empty=null;for(int n=0;n<list.getAdapter().getCount();n++)if(list.getAdapter().getItem(n)==null)empty=list.getAdapter().getView(n,null,list);assertNotNull(empty);assertTrue(text(empty).contains("Town Hall 14"));
    }
    @Test public void missingArmyItemsAreUnlockChecksInsteadOfFreeResearchQuotes(){
        PlannerActivity activity=sample();find(activity.getWindow().getDecorView(),"Army Upgrades").performClick();ListView list=activity.findViewById(R.id.planner_list);ListAdapter adapter=list.getAdapter();boolean section=false;
        for(int n=0;n<adapter.getCount();n++){Object value=adapter.getItem(n);if("Unlocks / Not in Export".equals(value))section=true;if(value instanceof UpgradePlanner.Row&&((UpgradePlanner.Row)value).fresh){assertTrue(section);assertTrue(text(adapter.getView(n,null,list)).contains("Check Unlock"));return;}}fail("Missing unlock example");
    }
    private String text(View view){String result=view instanceof TextView?((TextView)view).getText().toString():"";if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)result+=" "+text(((ViewGroup)view).getChildAt(n));return result;}
}
