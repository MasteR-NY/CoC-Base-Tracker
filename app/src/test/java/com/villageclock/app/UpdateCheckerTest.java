package com.villageclock.app;

import org.junit.Test;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import static org.junit.Assert.*;

public class UpdateCheckerTest {
    private static String release(String version){return "{\"tag_name\":\""+version+"\",\"draft\":false,\"prerelease\":false,\"assets\":[{\"name\":\"CoC-Base-Tracker.apk\",\"state\":\"uploaded\",\"browser_download_url\":\"https://github.com/MasteR-NY/CoC-Base-Tracker/releases/download/"+version+"/CoC-Base-Tracker.apk\"}]}";}
    @Test public void comparesVersionsNumericallyIncludingFutureDoubleDigitMinorReleases()throws Exception{
        assertTrue(UpdateChecker.compare("v1.10","1.9")>0);assertTrue(UpdateChecker.compare("v2.0","1.99")>0);
        assertEquals(0,UpdateChecker.compare("1.9.0","v1.9"));assertEquals(0,UpdateChecker.compare("1.9+build.3","1.9"));
        assertTrue(UpdateChecker.compare("1.9.1","1.9")>0);assertTrue(UpdateChecker.compare("1.8","1.9")<0);
    }
    @Test public void parsesPublishedReleaseAndIgnoresUntrustedNavigationUrls()throws Exception{
        UpdateChecker.Release result=UpdateChecker.parse(release("v2.0").replace("\"assets\"","\"html_url\":\"https://example.invalid/phishing\",\"assets\""));
        assertEquals("2.0",result.version);assertTrue(result.hasApk);assertEquals("https://github.com/MasteR-NY/CoC-Base-Tracker/releases/latest",UpdateChecker.RELEASES_URL);
    }
    @Test public void rejectsDraftsPrereleasesAndUnexpectedVersionFormats()throws Exception{
        for(String json:new String[]{release("1.9").replace("\"draft\":false","\"draft\":true"),release("1.9").replace("\"prerelease\":false","\"prerelease\":true"),release("1.9-beta"),release("release-latest"),"{}","not JSON"}){
            try{UpdateChecker.parse(json);fail("Accepted: "+json);}catch(UpdateChecker.CheckException expected){assertFalse(expected.getMessage().isEmpty());}
        }
    }
    @Test public void distinguishesReleaseWithoutDownloadableApk()throws Exception{
        assertFalse(UpdateChecker.parse(release("v2.0").replace(".apk",".zip")).hasApk);
        assertFalse(UpdateChecker.parse(release("v2.0").replace("uploaded","new")).hasApk);
    }
    @Test public void makesOnlyFixedAnonymousHttpsGetWithTimeoutsAndNoRedirects()throws Exception{
        FakeConnection connection=new FakeConnection(200,release("v2.0").getBytes(StandardCharsets.UTF_8));
        UpdateChecker.Release result=UpdateChecker.fetchLatest("1.9",url->{assertEquals(UpdateChecker.API_URL,url.toString());return connection;});
        assertEquals("2.0",result.version);assertEquals("GET",connection.getRequestMethod());assertFalse(connection.getInstanceFollowRedirects());
        assertEquals(12000,connection.getConnectTimeout());assertEquals(12000,connection.getReadTimeout());assertFalse(connection.getUseCaches());
        assertEquals("CoC-Base-Tracker/1.9",connection.getRequestProperty("User-Agent"));assertNull(connection.getRequestProperty("Authorization"));
        assertNull(connection.getRequestProperty("Cookie"));assertFalse(connection.getDoOutput());assertTrue(connection.closed);assertTrue(connection.disconnected);
    }
    @Test public void unavailableRateLimitedAndRedirectedRequestsNeverReportUpToDate()throws Exception{
        for(int status:new int[]{404,403,429,500,302}){FakeConnection connection=new FakeConnection(status,new byte[0]);
            try{UpdateChecker.fetchLatest("1.9",url->connection);fail("Accepted HTTP "+status);}catch(UpdateChecker.CheckException expected){assertTrue(connection.disconnected);}
        }
    }
    @Test public void boundsServerResponsesAndClosesConnectionsOnFailure()throws Exception{
        FakeConnection connection=new FakeConnection(200,new byte[512*1024+1]);
        try{UpdateChecker.fetchLatest("1.9",url->connection);fail("Accepted oversized response");}catch(UpdateChecker.CheckException expected){assertTrue(connection.closed);assertTrue(connection.disconnected);}
    }
    @Test public void apiRateLimitFallsBackToVerifiedPublicLatestRedirect()throws Exception{
        FakeConnection api=new FakeConnection(403,new byte[0]),page=new FakeConnection(302,new byte[0]);
        page.location="/MasteR-NY/CoC-Base-Tracker/releases/tag/v1.10";java.util.List<String> requested=new java.util.ArrayList<>();
        UpdateChecker.Release result=UpdateChecker.fetchLatest("1.9",url->{requested.add(url.toString());return url.toString().equals(UpdateChecker.API_URL)?api:page;});
        assertEquals(java.util.Arrays.asList(UpdateChecker.API_URL,UpdateChecker.RELEASES_URL),requested);
        assertEquals("1.10",result.version);assertFalse(result.apkStatusKnown);
        assertTrue(api.disconnected);assertTrue(page.disconnected);assertFalse(page.getInstanceFollowRedirects());
        assertEquals("text/html",page.getRequestProperty("Accept"));assertNull(page.getRequestProperty("Authorization"));assertNull(page.getRequestProperty("Cookie"));
    }
    @Test public void dnsFailureOnApiCanStillUseGithubWebsite()throws Exception{
        FakeConnection page=new FakeConnection(302,new byte[0]);page.location=UpdateChecker.RELEASES_URL.replace("latest","tag/v2.0");
        assertEquals("2.0",UpdateChecker.fetchLatest("1.9",url->{if(url.toString().equals(UpdateChecker.API_URL))throw new java.net.UnknownHostException();return page;}).version);
    }
    @Test public void rejectsRedirectsOutsideExactRepositoryAndUnstableTags()throws Exception{
        for(String location:new String[]{"https://evil.invalid/MasteR-NY/CoC-Base-Tracker/releases/tag/v2.0","http://github.com/MasteR-NY/CoC-Base-Tracker/releases/tag/v2.0","https://github.com@evil.invalid/MasteR-NY/CoC-Base-Tracker/releases/tag/v2.0","https://github.com/other/repo/releases/tag/v2.0","/MasteR-NY/CoC-Base-Tracker/releases/tag/v2.0-beta","/MasteR-NY/CoC-Base-Tracker/releases/tag/v2.0?next=evil","/MasteR-NY/CoC-Base-Tracker/releases/tag/v2.0%2Fother",null}){
            try{UpdateChecker.parseReleaseRedirect(location);fail("Accepted "+location);}catch(UpdateChecker.CheckException expected){ }
        }
    }
    @Test public void combinedFailuresKeepUsefulDetailsWithoutClaimingNoInternet()throws Exception{
        try{UpdateChecker.fetchLatest("1.10",url->{if(url.toString().equals(UpdateChecker.API_URL))throw new javax.net.ssl.SSLHandshakeException("untrusted");throw new java.net.UnknownHostException();});fail();}
        catch(UpdateChecker.CheckException error){assertTrue(error.getMessage().contains("SSLHandshakeException"));assertTrue(error.getMessage().contains("date/time"));assertTrue(error.getMessage().contains("Private DNS"));}
        assertTrue(UpdateChecker.failureDetail(new java.net.SocketTimeoutException()).contains("timed out"));
    }
    private static final class FakeConnection extends HttpsURLConnection {
        final int status;final byte[] body;boolean disconnected,closed;String location;
        FakeConnection(int status,byte[] body)throws Exception{super(new URL(UpdateChecker.API_URL));this.status=status;this.body=body;}
        @Override public int getResponseCode(){return status;}
        @Override public String getHeaderField(String name){return "Location".equalsIgnoreCase(name)?location:null;}
        @Override public InputStream getInputStream(){return new ByteArrayInputStream(body){@Override public void close()throws IOException{closed=true;super.close();}};}
        @Override public void disconnect(){disconnected=true;}
        @Override public boolean usingProxy(){return false;}
        @Override public void connect(){ }
        @Override public String getCipherSuite(){return "TLS";}
        @Override public Certificate[] getLocalCertificates(){return null;}
        @Override public Certificate[] getServerCertificates(){return null;}
    }
}
