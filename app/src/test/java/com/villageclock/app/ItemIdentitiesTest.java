package com.villageclock.app;

import android.content.Context;
import android.graphics.BitmapFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk={26,29,35})
public class ItemIdentitiesTest {
    @Test public void nineModuleNamesCapsAndPortraitsAreResolved()throws Exception{
        Context c=RuntimeEnvironment.getApplication();ItemIdentities catalog=ItemIdentities.load(c);int count=0;
        for(int id=102000033;id<=102000041;id++){ItemIdentities.Entry entry=catalog.item(id);assertNotNull(entry);assertEquals(10,entry.cap);assertFalse(entry.name.contains("#"));assertNotEquals(R.drawable.ic_village,ItemArt.icon(c,id));count++;}
        assertEquals(9,count);assertEquals("Hot Candle Hitpoints",Store.catalog(c).name(102000033));assertEquals("Poison Effect",catalog.item(102000038).shortName);assertEquals("Splash Damage",catalog.item(102000041).shortName);
        assertEquals(R.drawable.module_time,ItemIdentities.moduleSymbol("Duration"));assertEquals(R.drawable.module_explosion,ItemIdentities.moduleSymbol("Splash Damage"));
    }
    @Test public void helperIdsUseCorrectNamesAndTheirOwnArtwork(){
        Context c=RuntimeEnvironment.getApplication();assertEquals("Alchemist",Store.catalog(c).name(93000002));assertEquals("Prospector",Store.catalog(c).name(93000003));
        assertEquals("Alchemist",ItemIdentities.helperName(93000002));assertEquals("Prospector",ItemIdentities.helperName(93000003));assertNotEquals(ItemArt.icon(c,93000002),ItemArt.icon(c,93000003));
        for(int id=93000000;id<=93000003;id++)assertNotNull(BitmapFactory.decodeResource(c.getResources(),ItemArt.icon(c,id)));
    }
    @Test public void knownCosmeticsResolveOfflineAndUnverifiedIdsDoNotBorrowNames(){
        Context c=RuntimeEnvironment.getApplication();ItemIdentities catalog=ItemIdentities.load(c);
        assertEquals("Super Squad King",catalog.item(52000136).name);assertEquals("Shadow Scenery",catalog.item(60000017).name);
        for(int id:new int[]{52000136,60000017,18000001,8000065,82000008})assertNotEquals(R.drawable.ic_village,ItemArt.icon(c,id));
        for(int id:new int[]{52000268,52000337,18000244,18000248,99999999})assertNull(catalog.item(id));
    }
    @Test @Config(sdk=35) public void allDeclaredArtworkResourcesExistAndDecode(){
        Context c=RuntimeEnvironment.getApplication();for(ItemIdentities.Entry entry:ItemIdentities.load(c).items())if(!entry.icon.isEmpty()){
            int id=c.getResources().getIdentifier(entry.icon,"drawable",c.getPackageName());assertNotEquals("Missing "+entry.icon,0,id);
            android.graphics.Bitmap bitmap=BitmapFactory.decodeResource(c.getResources(),id);assertNotNull(entry.icon,bitmap);assertTrue(bitmap.getWidth()<=128);assertTrue(bitmap.getHeight()<=128);bitmap.recycle();
        }
    }
}
