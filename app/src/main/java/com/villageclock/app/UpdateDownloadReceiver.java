package com.villageclock.app;

import android.app.*;
import android.content.*;

/** A completion alert only; the installer is opened by a foreground button. */
public final class UpdateDownloadReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        long id=UpdateDownloads.id(context);
        if(!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())||id==-1||intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-2)!=id)return;
        PendingResult pending=goAsync();Context app=context.getApplicationContext();
        new Thread(()->{
            try{
                UpdateDownloads.verify(app);
                NotificationManager nm=app.getSystemService(NotificationManager.class);
                if(nm==null||!nm.areNotificationsEnabled())return;
                nm.createNotificationChannel(new NotificationChannel("app-updates","App Updates",NotificationManager.IMPORTANCE_DEFAULT));
                PendingIntent open=PendingIntent.getActivity(app,90,UpdateActivity.intent(app,null),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                nm.notify(90,new Notification.Builder(app,"app-updates").setSmallIcon(R.drawable.ic_village)
                    .setContentTitle("CoC Base Tracker update ready").setContentText("v"+UpdateDownloads.version(app)+" · Tap to review and install")
                    .setContentIntent(open).setAutoCancel(true).build());
            }catch(Exception ignored){/* The download screen reports verification failures and offers retry. */}
            finally{pending.finish();}
        },"update-verification").start();
    }
}
