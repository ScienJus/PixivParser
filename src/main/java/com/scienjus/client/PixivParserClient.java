package com.scienjus.client;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.scienjus.callback.DownloadCallback;
import com.scienjus.callback.WorkCallback;
import com.scienjus.config.PixivParserConfig;
import com.scienjus.filter.WorkFilter;
import com.scienjus.model.Page;
import com.scienjus.model.Rank;
import com.scienjus.model.Work;
import com.scienjus.param.ParserParam;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 使用Pixiv iOS APi的客户端
 * Pixiv Parser Client use Pixiv iOS Api
 */
public class PixivParserClient {

    /**'
     * 日志文件
     * the logger
     */
    private static final Logger LOGGER = Logger.getLogger(PixivParserClient.class);

    /**
     * 日期格式化
     * format date to url param
     */
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * 用户名
     * the username to login
     */
    private String username;

    /**
     * 密码
     * the password to login
     */
    private String password;

    /**
     * 鉴权Token
     * the access token to use pixiv api
     */
    private String accessToken;

    /**
     * http请求发送端
     * send to pixiv
     */
    private CloseableHttpClient client;

    /**
     * 设置用户名
     * set your username (pixiv id)
     * @param username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 设置密码
     * set your pixiv password
     * @param password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public PixivParserClient() {
        client = HttpClients.createDefault();
    }

    /**
     * 登陆表单的构成
     * params that need to be submitted when logged in
     * @return
     */
    private UrlEncodedFormEntity buildLoginForm() {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("client_id", PixivParserConfig.CLIENT_ID));
        params.add(new BasicNameValuePair("client_secret", PixivParserConfig.CLIENT_SECRET));
        params.add(new BasicNameValuePair("grant_type", "password"));
        params.add(new BasicNameValuePair("username", username));
        params.add(new BasicNameValuePair("password", password));
        params.add(new BasicNameValuePair("get_secure_url", "true"));
        return new UrlEncodedFormEntity(params, PixivParserConfig.CHARSET);
    }

    /**
     * 登录
     * login and get access_token
     * @return access_token
     */
    public boolean login() {
        if (username == null || password == null) {
            LOGGER.error("username or password is empty！");
            return false;
        }
        LOGGER.info("The currently logged in user is：" + username);
        HttpPost post = new HttpPost(PixivParserConfig.LOGIN_URL);
        post.setEntity(buildLoginForm());

        // get current time in ISO format
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        String localTime = (formatter.format(now)); // e.g. 2019-09-02T22:10:52+01:00

        // generate X-Client-Hash = md5(localTime + HASH_SECRET)
        String clientHash = "";
        try {
            MessageDigest messageDigest;

            messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update((localTime + PixivParserConfig.HASH_SECRET).getBytes(PixivParserConfig.CHARSET));
            byte[] digiest = messageDigest.digest();
            clientHash = DatatypeConverter.printHexBinary(digiest).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error(e.getMessage());
        }

        // set headers
        post.setHeader("User-Agent", PixivParserConfig.USER_AGENT);
        post.setHeader("X-Client-Time", localTime);
        post.setHeader("X-Client-Hash", clientHash);

        try (CloseableHttpResponse response = client.execute(post)) {
            if (response.getStatusLine().getStatusCode() == 200) {
                LOGGER.info("login successful！");
                this.accessToken = getAccessToken(response);
                return true;
            } else {
                LOGGER.error("Login failed！Please check if the username or password is correct");
                return false;
            }
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
            return false;
        }
    }

    /**
     * 通过id获取作品
     * get illust by id
     * @param workId
     * @return
     */
    public Work getWork(int workId) {
        String url = buildDetailUrl(workId);
        HttpGet get = defaultHttpGet(url);
        try (CloseableHttpResponse response = client.execute(get)) {
            JSONObject json = getResponseContent(response);
            return JSON.parseObject(json.getJSONArray("response").getJSONObject(0).toJSONString(), Work.class);
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
        return null;
    }

    /**
     * 从Response中获得AccessToken
     * get access token from response
     * @param response
     * @return
     */
    private static String getAccessToken(CloseableHttpResponse response) throws IOException {
        return getResponseContent(response).getJSONObject("response").getString("access_token");
    }

    /**
     * 获得返回数据
     * get json content from response
     * @param response
     * @return
     */
    private static JSONObject getResponseContent(CloseableHttpResponse response) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), PixivParserConfig.CHARSET));
        StringBuilder buffer = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            buffer.append(line);
        }
        return JSON.parseObject(buffer.toString());
    }

    /**
     * 创建默认的httpGet请求
     * create a defalut http get
     * @param url
     * @return
     */
    private HttpGet defaultHttpGet(String url) {
        url = url.replace(" ", "%20");
        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", String.format("Bearer %s", this.accessToken));
        get.setHeader("Referer", "http://spapi.pixiv.net/");
        get.setHeader("User-Agent", PixivParserConfig.USER_AGENT);
        return get;
    }

    /**
     * 获得当天的排行榜
     * get today ranking
     * @return
     */
    public Rank ranking() {
        return ranking(null);
    }

    /**
     * 获得某天的排行榜
     * get ranking on one day
     * @param date
     * @return
     */
    public Rank ranking(Date date) {
        HttpGet get;
        JSONObject json;
        int page = PixivParserConfig.START_PAGE;
        Rank rank = null;
        while (true) {
            String url = buildRankUrl(date, page);
            get = defaultHttpGet(url);
            try (CloseableHttpResponse response = client.execute(get)) {
                json = getResponseContent(response);
                JSONArray body = JSON.parseObject(json.toJSONString()).getJSONArray("response");
                if (rank == null) {
                    rank = JSON.parseObject(body.getJSONObject(0).toJSONString(), Rank.class);
                } else {
                    rank.getWorks().addAll(JSON.parseObject(body.getJSONObject(0).toJSONString(), Rank.class).getWorks());
                }
                int nextPage = getNextPage(json);
                if (nextPage != PixivParserConfig.NO_NEXT_PAGE) {
                    page = nextPage;
                } else {
                    return rank;
                }
            } catch (IOException e) {
                LOGGER.error(e.getMessage());
            }
        }
    }

    /**
     * 查询作品
     * search illusts by key word
     * @param keyWord
     * @return
     */
    public List<Work> search(String keyWord) {
        return search(keyWord, new ParserParam());
    }

    /**
     * 查询作品
     * search illusts by key word with custom param
     * @param keyWord
     * @return
     */
    public List<Work> search(String keyWord, ParserParam param) {
        int page = PixivParserConfig.START_PAGE;
        List<Work> works = new ArrayList<>();
        while (true) {
            String url = buildSearchUrl(keyWord, page);
            HttpGet get = defaultHttpGet(url);
            try (CloseableHttpResponse response = client.execute(get)) {
                JSONObject json = getResponseContent(response);
                JSONArray body = json.getJSONArray("response");
                for (int i = 0; i < body.size(); i++) {
                    Work work = JSON.parseObject(body.getJSONObject(i).toJSONString(), Work.class);
                    WorkFilter filter = param.getFilter();
                    if (filter == null || filter.doFilter(work)) {
                        WorkCallback callback = param.getCallback();
                        if (callback != null) {
                            callback.onFound(work);
                        }
                        works.add(work);
                        int limit = param.getLimit();
                        if (limit != PixivParserConfig.NO_LIMIT && works.size() >= limit) {
                            return works;
                        }
                    }
                }
                int nextPage = getNextPage(json);
                if (nextPage != PixivParserConfig.NO_NEXT_PAGE) {
                    page = nextPage;
                } else {
                    return works;
                }
            } catch (IOException e) {
                LOGGER.error(e.getMessage());
            }
        }
    }

    /**
     * 获得指定作者的作品
     * get illusts by author
     * @param authorId
     * @return
     */
    public List<Work> byAuthor(int authorId) {
        return byAuthor(authorId, new ParserParam());
    }

    /**
     * 获得指定作者的作品
     * get illusts by author with custom param
     * @param authorId
     * @return
     */
    public List<Work> byAuthor(int authorId, ParserParam param) {
        int page = PixivParserConfig.START_PAGE;
        List<Work> works = new ArrayList<>();
        while (true) {
            String url = buildByAuthorUrl(authorId, page);
            HttpGet get = defaultHttpGet(url);
            try (CloseableHttpResponse response = client.execute(get)) {
                JSONObject json = getResponseContent(response);
                JSONArray body = json.getJSONArray("response");
                for (int i = 0; i < body.size(); i++) {
                    Work work = JSON.parseObject(body.getJSONObject(i).toJSONString(), Work.class);
                    WorkFilter filter = param.getFilter();
                    if (filter == null || filter.doFilter(work)) {
                        WorkCallback callback = param.getCallback();
                        if (callback != null) {
                            callback.onFound(work);
                        }
                        works.add(work);
                        int limit = param.getLimit();
                        if (limit != PixivParserConfig.NO_LIMIT && works.size() >= limit) {
                            return works;
                        }
                    }
                }
                int nextPage = getNextPage(json);
                if (nextPage != PixivParserConfig.NO_NEXT_PAGE) {
                    page = nextPage;
                } else {
                    return works;
                }
            } catch (IOException e) {
                LOGGER.error(e.getMessage());
            }
        }

    }

    /**
     * byAuthor请求的url
     * build byAuthor api url
     * @param authorId
     * @param page
     * @return
     */
    public static String buildByAuthorUrl(int authorId, int page) {
        Map<String, String> params = getCommonParams(page);
        params.put("mode", "exact_tag");
        params.put("per_page", "30");
        return buildGetUrl(PixivParserConfig.AUTHOR_DETAIL_URL.replace("{authorId}", String.valueOf(authorId)), params);
    }

    /**
     * 请求排行榜的url
     * build ranking api url
     * @param date
     * @param page
     * @return
     */
    public static String buildRankUrl(Date date, int page) {
        Map<String, String> params = getCommonParams(page);
        params.put("mode", "daily");
        params.put("per_page", "50");
        if (date != null) {
            params.put("date", FORMAT.format(date));
        }
        return buildGetUrl(PixivParserConfig.RANK_URL, params);
    }

    /**
     * 请求搜索的url
     * the search api url
     * @param keyWord
     * @param page
     * @return
     */
    public static String buildSearchUrl(String keyWord, int page) {
        Map<String, String> params = getCommonParams(page);
        params.put("q", keyWord);
        if (keyWord.split(" ").length == 1) {
            params.put("mode", "exact_tag");
        } else {
            params.put("mode", "text");
        }
        params.put("per_page", "30");
        return buildGetUrl(PixivParserConfig.SEARCH_URL, params);
    }

    /**
     * 公用参数
     * the common params
     * @param page
     * @return
     */
    private static Map<String, String> getCommonParams(int page) {
        Map<String, String> params = new HashMap<>();
        params.put("image_size", "profile_image_sizes");
        params.put("profile_image_sizes", "px_170x170");
        params.put("include_sanity_level", "true");
        params.put("include_stats", "true");
        params.put("period", "all");
        params.put("order", "desc");
        params.put("sort", "date");
        params.put("page", String.valueOf(page));
        return params;
    }

    /**
     * 作品详情url
     * the getWork api url
     * @param workId
     * @return
     */
    private static String buildDetailUrl(int workId) {
        Map<String, String> params = new HashMap<>();
        params.put("image_sizes", "small,medium,large");
        params.put("include_stats", "true");
        return buildGetUrl(PixivParserConfig.ILLUST_DETAIL_URL.replace("{illustId}", String.valueOf(workId)), params);
    }

    /**
     * 将参数和url拼接起来
     * joint url and params
     * @param url
     * @param params
     * @return
     */
    private static String buildGetUrl(String url, Map<String, String> params) {
        if (params.isEmpty()) {
            return url;
        }
        StringBuilder buffer = new StringBuilder(url);
        if (!url.endsWith("?")) {
            buffer.append("?");
        }
        for (Map.Entry<String, String> entry : params.entrySet()) {
            buffer.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        buffer.deleteCharAt(buffer.length() - 1);
        return buffer.toString();
    }

    /**
     * 获得下一页
     * get next page number
     * @param json
     * @return
     */
    public static int getNextPage(JSONObject json) {
        Integer nextPage = ((JSONObject) json.get("pagination")).getInteger("next");
        if (nextPage != null) {
            return nextPage;
        }
        return PixivParserConfig.NO_NEXT_PAGE;
    }

    /**
     * 关闭client
     * close the client
     */
    public void close() {
        try {
            client.close();
        } catch (IOException e) {
            LOGGER.error("关闭客户端失败：" + e.getMessage());
        }
    }

    public static void download(Work work, DownloadCallback callback) {
        DownloadTask task = new DownloadTask(work, callback);
        new Thread(task).start();
    }

    private static class DownloadTask implements Runnable {

        private Work work;

        private DownloadCallback callback;

        public DownloadTask(Work work, DownloadCallback callback) {
            this.work = work;
            this.callback = callback;
        }

        @Override
        public void run() {
            if (work.isManga()) {
                List<byte[]> files = new ArrayList<>();
                for (Page page : work.getMetadata().getPages()) {
                    files.add(downloadImage(page.getImageUrls().getLarge()));
                }
                if (callback != null) {
                    callback.onMangaFinished(work, files);
                }
            } else {
                byte[] file = downloadImage(work.getImageUrls().getLarge());
                if (callback != null) {
                    callback.onIllustFinished(work, file);
                }
            }
        }

        private byte[] downloadImage(String url) {
            HttpGet get = new HttpGet(url);
            get.setHeader("Referer", "http://www.pixiv.net");
            try (CloseableHttpClient client = HttpClients.createDefault();
                 CloseableHttpResponse response = client.execute(get);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = response.getEntity().getContent().read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                return out.toByteArray();
            } catch (IOException e) {
                LOGGER.error(e.getMessage());
            }
            return null;
        }
    }

}
