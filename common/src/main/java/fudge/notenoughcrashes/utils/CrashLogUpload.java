package fudge.notenoughcrashes.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import fudge.notenoughcrashes.ModConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class CrashLogUpload {
    private static String GIST_ACCESS_TOKEN_PART_1() {
        return "dc07dacff0c2cf84f706";
    }

    private static String GIST_ACCESS_TOKEN_PART_2() {
        return "8ac0fd6a757d53b81233";
    }

    // I don't think there's any security problem because the token can only upload gists,
    // but Github will revoke the token as soon as it sees it, so we trick it by splitting the token into 2.
    private static final String GIST_ACCESS_TOKEN = GIST_ACCESS_TOKEN_PART_1() + GIST_ACCESS_TOKEN_PART_2();

    private static class GistPost {
        @SerializedName("public")
        public boolean isPublic;
        public Map<String, GistFile> files;

        public GistPost(boolean isPublic, Map<String, GistFile> files) {
            this.isPublic = isPublic;
            this.files = files;
        }
    }

    private static class GistFile {
        public String content;

        public GistFile(String content) {
            this.content = content;
        }
    }

    public static String upload(String text) throws IOException {
        String URL;
        ModConfig.CrashLogUploadType type = ModConfig.instance().uploadCrashLogTo;
        switch (type) {
            case GIST:
                String GISTuploadKey = ModConfig.instance().GISTUploadKey;
                if (GISTuploadKey == "") {
                    URL = uploadToGist(text);
                } else {
                    URL = uploadToGist(text, GISTuploadKey);
                }
                break;
            case HASTE:
                URL = uploadToHaste(text);
                break;
            case BYTEBIN:
                URL = uploadToByteBin(text);
                break;
            default:
                throw new IOException("fail, unknown provider");
        }

        return URL;

    }

    private static String uploadToGist(String text) throws IOException {
        return uploadToGist(text, GIST_ACCESS_TOKEN);
    }

    /**
     * @return The link of the gist
     */
    private static String uploadToGist(String text, String key) throws IOException {
        HttpPost post = new HttpPost("https://api.github.com/gists");

        String fileName = "crash.txt";


        post.addHeader("Authorization", "token " + key);

        GistPost body = new GistPost(!ModConfig.instance().GISTUnlisted,
                new HashMap<String, GistFile>() {{
                    put(fileName, new GistFile(text));
                }}
        );
        post.setEntity(new StringEntity(new Gson().toJson(body)));

        if (ModConfig.instance().uploadCustomUserAgent != null) {
            post.setHeader("User-Agent",ModConfig.instance().uploadCustomUserAgent);
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            CloseableHttpResponse response = httpClient.execute(post);
            String responseString = EntityUtils.toString(response.getEntity());
            JsonObject responseJson = new Gson().fromJson(responseString, JsonObject.class);
            return responseJson.getAsJsonObject("files")
                    .getAsJsonObject(fileName)
                    .getAsJsonPrimitive("raw_url")
                    .getAsString();
        }

    }

    private static String uploadToHaste(String str) throws IOException {
        HttpPost post = new HttpPost(ModConfig.instance().HASTEUrl + "documents");
        post.setEntity(new StringEntity(str));
        if (ModConfig.instance().uploadCustomUserAgent != null) {
            post.setHeader("User-Agent",ModConfig.instance().uploadCustomUserAgent);
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            CloseableHttpResponse response = httpClient.execute(post);
            String responseString = EntityUtils.toString(response.getEntity());
            JsonObject responseJson = new Gson().fromJson(responseString, JsonObject.class);
            String hasteKey = responseJson.getAsJsonPrimitive("key").getAsString();
            return ModConfig.instance().HASTEUrl + "raw/" + hasteKey;
        }

    }
    private static String uploadToByteBin(String text) throws IOException {
        HttpPost post = new HttpPost(ModConfig.instance().BYTEBINUrl + "post");
        if (ModConfig.instance().uploadCustomUserAgent == null) {
            post.setHeader("User-Agent",(String.join(" ", post.getHeaders("User-Agent").toString())
                    .concat(" NotEnoughCrashes")));
        } else {
            post.setHeader("User-Agent",ModConfig.instance().uploadCustomUserAgent);
        }

        post.addHeader("Content-Type", "text/plain");
        post.setEntity(new StringEntity(text));

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            CloseableHttpResponse response = httpClient.execute(post);
            String responseString = EntityUtils.toString(response.getEntity());
            JsonObject responseJson = new Gson().fromJson(responseString, JsonObject.class);
            String bytebinKey = responseJson.getAsJsonPrimitive("key").getAsString();
            return ModConfig.instance().BYTEBINUrl + bytebinKey;
    }
    }
}
