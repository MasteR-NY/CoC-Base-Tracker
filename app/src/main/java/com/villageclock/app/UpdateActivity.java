package com.villageclock.app;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.concurrent.*;

/** An explicit download/verify/install flow. Never installs silently or launches from the background. */
public final class UpdateActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TextView status,detail;private ProgressBar progress;private Button install,retry,cancel;
    private boolean resumed,busy,ready;private long verifiedId=-1;
    private UpdateChecker.Release release;
    private final Runnable poll=this::refresh;
    static Intent intent(Context context,UpdateChecker.Release release){
        Intent intent=new Intent(context,UpdateActivity.class);
        if(release!=null){
            try{
                JSONObject json=new JSONObject().put("tag_name",release.tag).put("draft",false).put("prerelease",false);
                JSONArray assets=new JSONArray();
                if(release.hasApk)assets.put(new JSONObject().put("name",release.fileName).put("state","uploaded").put("browser_download_url",release.downloadUrl).put("size",release.bytes).put("digest",release.sha256.isEmpty()?"":"sha256:"+release.sha256));
                json.put("assets",assets);intent.putExtra("release",json.toString()).putExtra("redirect",!release.apkStatusKnown);
            }catch(JSONException impossible){throw new IllegalArgumentException(impossible);}
        }
        return intent;
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);createViews();
        try{
            String json=getIntent().getStringExtra("release");
            if(json!=null){release=UpdateChecker.parse(json);if(getIntent().getBooleanExtra("redirect",false))release=UpdateChecker.parseReleaseRedirect("/MasteR-NY/CoC-Base-Tracker/releases/tag/"+release.tag);}
            if(getIntent().getBooleanExtra("preview",false)){showReady("1.16");return;}
            if(release!=null&&(UpdateDownloads.id(this)==-1||!release.version.equals(UpdateDownloads.version(this))))startDownload();
        }catch(Exception error){showError(error);}
    }
    private void createViews(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xFF24180F);
        LinearLayout content=column();content.setPadding(dp(20),dp(24),dp(20),dp(24));scroll.addView(content);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());content.setPadding(dp(20)+bars.left,dp(24)+bars.top,dp(20)+bars.right,dp(24)+bars.bottom);}
            else content.setPadding(dp(20),dp(24)+insets.getSystemWindowInsetTop(),dp(20),dp(24)+insets.getSystemWindowInsetBottom());return insets;
        });
        button(content,"‹ Back",false,v->finish());space(content,20);
        content.addView(text("App Update",30,0xFFFFC531,true));content.addView(text("CoC Base Tracker v"+BuildConfig.VERSION_NAME,15,0xFFCDBDA5,false));space(content,22);
        LinearLayout card=column();card.setBackgroundResource(R.drawable.card_bg);card.setPadding(dp(18),dp(18),dp(18),dp(18));content.addView(card);
        status=text("Preparing Update",23,0xFFFFF6E6,true);status.setId(R.id.download_status);card.addView(status);space(card,12);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);card.addView(progress,new LinearLayout.LayoutParams(-1,dp(12)));space(card,12);
        detail=text("The download is managed by Android. You can keep using the tracker while it finishes.",15,0xFFCDBDA5,false);detail.setId(R.id.download_detail);card.addView(detail);space(card,20);
        install=button(card,"Install Update",true,v->install());install.setId(R.id.install_update);install.setVisibility(View.GONE);
        retry=button(card,"Retry Download",true,v->startDownload());retry.setVisibility(View.GONE);space(card,8);
        cancel=button(card,"Cancel Download",false,v->{try{UpdateDownloads.cancel(this);finish();}catch(Exception e){showError(e);}});
        space(content,20);content.addView(text("Android asks you to confirm installation. Your saved accounts and widgets stay in place when you update.",14,0xFFCDBDA5,false));
        space(content,16);button(content,"Open Releases",false,v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(UpdateChecker.RELEASES_URL)));}catch(ActivityNotFoundException missing){Toast.makeText(this,"No browser is available.",Toast.LENGTH_LONG).show();}});
    }
    @Override protected void onResume(){super.onResume();resumed=true;if(!getIntent().getBooleanExtra("preview",false))refresh();}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(poll);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
    private void startDownload(){
        if(busy)return;ready=false;verifiedId=-1;
        try{
            if(release==null)throw new java.io.IOException("Return to Check for Updates to find the latest release.");
            UpdateDownloads.cancel(this);UpdateDownloads.start(this,release);retry.setVisibility(View.GONE);cancel.setText("Cancel Download");refresh();
        }catch(Exception error){showError(error);}
    }
    private void refresh(){
        handler.removeCallbacks(poll);if(!resumed||busy||isFinishing())return;
        long id=UpdateDownloads.id(this);if(id==-1){showError(new java.io.IOException("No update download is saved. Return to Check for Updates."));return;}
        if(ready&&verifiedId==id)return;
        busy=true;worker.execute(()->{
            UpdateDownloads.Status result=null;Exception failure=null;boolean complete=false;
            try{result=UpdateDownloads.status(this,id);
                if(result.total>UpdateDownloads.MAX_APK||result.downloaded>UpdateDownloads.MAX_APK){UpdateDownloads.cancel(this);throw new java.io.IOException("The APK exceeds the 100 MB download limit.");}
                if(result.state==DownloadManager.STATUS_FAILED)throw new java.io.IOException(UpdateDownloads.downloadFailure(result.reason));
                if(result.state==DownloadManager.STATUS_SUCCESSFUL){UpdateDownloads.verify(this);complete=true;}
            }catch(Exception error){failure=error;}
            UpdateDownloads.Status value=result;Exception error=failure;boolean done=complete;
            handler.post(()->{busy=false;if(isDestroyed()||isFinishing())return;
                if(error!=null){showError(error);return;}
                if(done){verifiedId=id;showReady(UpdateDownloads.version(this));return;}
                status.setText(value.state==DownloadManager.STATUS_PAUSED?"Download Waiting":"Downloading v"+UpdateDownloads.version(this));
                progress.setVisibility(View.VISIBLE);progress.setIndeterminate(value.total<=0);if(value.total>0)progress.setProgress((int)(value.downloaded*100/value.total));
                detail.setText(size(value.downloaded)+(value.total>0?" / "+size(value.total):"")+"\nYou can leave this screen. Reopen Update Download from the home screen when it finishes.");
                if(resumed)handler.postDelayed(poll,750);
            });
        });
    }
    void showReady(String version){ready=true;status.setText("v"+version+" Ready to Install");progress.setVisibility(View.GONE);detail.setText("Download complete. Package, version and signing key checks passed.\n\nTap Install Update to continue with Android’s installer.");install.setVisibility(View.VISIBLE);retry.setVisibility(View.GONE);cancel.setText("Delete Download");}
    private void showError(Exception error){ready=false;status.setText("Update Unavailable");progress.setVisibility(View.GONE);detail.setText(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());install.setVisibility(View.GONE);retry.setVisibility(release==null?View.GONE:View.VISIBLE);}
    private void install(){
        if(!ready||busy||getIntent().getBooleanExtra("preview",false))return;
        if(!getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(this).setTitle("Allow This Update").setMessage("In Android Settings, allow CoC Base Tracker to install apps. Return here and tap Install Update again.")
                .setPositiveButton("Open Settings",(d,w)->{try{startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException missing){showError(new java.io.IOException("Open Android Settings → Apps → Special access → Install unknown apps and allow CoC Base Tracker."));}}).setNegativeButton("Later",null).show();return;
        }
        busy=true;install.setEnabled(false);worker.execute(()->{
            Uri uri=null;Exception failure=null;try{uri=UpdateDownloads.verify(this);}catch(Exception e){failure=e;}Uri verified=uri;Exception error=failure;
            handler.post(()->{busy=false;if(isDestroyed()||isFinishing())return;install.setEnabled(true);if(error!=null){showError(error);return;}if(!resumed)return;
                try{startActivity(installIntent(verified));}catch(ActivityNotFoundException missing){showError(new java.io.IOException("Android’s package installer is unavailable."));}
            });
        });
    }
    static Intent installIntent(Uri uri){Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("CoC Base Tracker update",uri));return intent;}
    private String size(long bytes){return String.format(java.util.Locale.getDefault(),"%.1f MB",Math.max(0,bytes)/1048576.0);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(bold?getResources().getFont(R.font.lilita_one):Typeface.create("sans-serif",Typeface.NORMAL));t.setLineSpacing(dp(2),1);return t;}
    private Button button(LinearLayout parent,String value,boolean primary,View.OnClickListener click){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(16);b.setTypeface(getResources().getFont(R.font.lilita_one));b.setTextColor(primary?0xFF3B2508:0xFFFFC531);b.setBackgroundResource(primary?R.drawable.btn_primary:R.drawable.btn_secondary);b.setMinHeight(dp(48));b.setPadding(dp(10),dp(5),dp(10),dp(5));b.setOnClickListener(click);parent.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private void space(LinearLayout p,int n){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
}
