
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import org.apache.commons.validator.routines.UrlValidator;
import org.bson.Document;

import com.mongodb.BasicDBObject;
import com.mongodb.Block;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class Main {

    public static final List<String> knowntypes = Arrays.asList("csv", "avi", "mp4", "xsd", "xlsx", "rdf", "json", "pdf",
            "xml", "xls", "php", "geojson", "zip", "rar", "txt", "tsv", "tgz", "jpg", "png", "docx", "doc", "odt",
            "ods", "7z", "py", "js", "epub", "rtf", "xlsm", "xsl", "wfsx", "gif", "gml32", "cgi", "nsf", "kmz",
            "kml+xml", "rss", "gml3", "gml2", "gml31", "rss+xml", "gmz", "xsp", "shape-zip", "gml", "gtif", "wfs",
            "tfw", "aspx", "map", "jsp", "shtml", "n3", "tif", "gz", "jp2", "wmv", "ppt", "pptx", "ttl", "kml", "wms",
            "nc", "turtle", "summary", "gpkg", "jpw", "exe", "jsonld", "jsf", "asp", "shp", "kmz xml", "cfm", "tar",
            "tcl", "svc", "sdmx", "n3", "ntriples", "rdf+xml", "nt", "nq", "nquads", "trig", "trix", "rdfs", "");

    public static final List<String> rdftypes = Arrays.asList("n3", "ntriples", "nc", "rdf+xml", "rdfs", "xml", "xls", "xsd", "rdf", "nt", "nq", "nquads", "trig", "trix", "json", "ttl", "turtle", "jsonld");

    public static Properties props;

    public static String urlCheck(String url) {
        try {
            String[] split = url.split("\\.");
            String urltype = split[split.length - 1];
            urltype = urltype.split("\\?")[0];
            urltype = urltype.toLowerCase();
            if (urltype.contains("format="))
                urltype = urltype.split("format=")[1];
            if (urltype.contains("#"))
                urltype = urltype.split("#")[0];
            // System.out.println(url + "\n" + urltype + "\n\n\n");
            if (urltype.split("/")[0].equals("application"))
                urltype = urltype.split("/")[1];
            else
                urltype = urltype.split("/")[0];
            if (knowntypes.contains(urltype.split("&")[0]))
                urltype = urltype.split("&")[0];
            if (urltype.contains("&type="))
                if (knowntypes.contains(urltype.split("&type=")[1]))
                    urltype = urltype.split("&type=")[1];
            return urltype;
        } catch (Exception e) {
            return "";
        }
    }

    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry
                        .comparingByValue(/* Collections.reverseOrder() */))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public static int getResponseCode(String url) throws MalformedURLException, IOException {
        URLConnection con = new URL(url).openConnection();
        HttpURLConnection http = (HttpURLConnection) con;
        return http.getResponseCode();
    }

    public static void getCriticalURLs() {
        MongoClient client = new MongoClient("localhost", 27017);
        MongoDatabase database = client.getDatabase("RDFDump");
        MongoCollection<Document> collection = database.getCollection("Dump");
        FindIterable<Document> it = collection.find().noCursorTimeout(true);
        it.forEach(new Block<Document>() {
            @Override
            public void apply(Document document) {
                if (document != null) {
                    String url = (String) document.get("downloadURL");
                    if (url != null) {
                        String type = urlCheck(url);
                        if (!knowntypes.contains(type.trim())) {
                            database.getCollection("Critical_URLs").insertOne(document);
                        }
                    }
                }
            }
        });
        client.close();
        /*
		 * sortByValue(types).forEach((String key, Integer value) -> {
		 * System.out.println(key + ": " + value); });
		 */
    }

    public static boolean worker(int skip, int limit, boolean auth) {
        try {
            MongoClient client;
            if (auth) client = clientInit();
            else client = new MongoClient(props.getProperty("ip"), Integer.parseInt(props.getProperty("port")));

            MongoDatabase database = client.getDatabase(props.getProperty("database"));
            MongoDatabase newdatadb = client.getDatabase("NewData");
            MongoDatabase cachedb = client.getDatabase("URLCache");
            MongoCollection<Document> collection = database.getCollection(props.getProperty("collection"));
            String[] schemes = {"http", "https"};
            UrlValidator urlValidator = new UrlValidator(schemes);
            // We don't want resources, that are already tagged as HTML, since that got nearly no results in its own run
            BasicDBObject query = new BasicDBObject("format", new BasicDBObject("$ne", "HTML"));
            FindIterable<Document> it = collection.find(query).noCursorTimeout(true).skip(skip).limit(limit);
            it.forEach(new Block<Document>() {
                @Override
                public void apply(Document document) {
                    try {
                        // get url
                        String url = document.getString(props.getProperty("urlField"));
                        String type = document.getString(props.getProperty("formatField"));
                        String realType = "";
                        int responseCode = 0;
                        // connect
                        if (url != null && urlValidator.isValid(url)) {
                            URL u = new URL(url);
                            HttpURLConnection http = (HttpURLConnection) u.openConnection();
                            http.setConnectTimeout(Integer.parseInt(props.getProperty("timeout")));
                            // get content, type & response code
                            responseCode = http.getResponseCode();
                            if (responseCode >= 200 && responseCode <= 299) {
                                // Resource is OK! -> go on.
                                InputStream is = (InputStream) http.getContent();
                                // get normalized Content Type
                                realType = http.getContentType();
                                document.put("normalized_format", realType);
                                if (realType.contains("text/html") && !type.trim().toLowerCase().contains("html")) {
                                    URI uri = u.toURI();
                                    // html, but not labeled as one? -> Let's
                                    // check this!
                                    for (String localUrl : crawl(is)) {
                                        // convert every local URL to global URL
                                        if (!localUrl.startsWith("javascript")) {
                                            String localURI = new URI(null, null, localUrl, null, null).toString();
                                            URL newUrl = uri.resolve(localURI).toURL();
                                            if (urlValidator.isValid(newUrl.toString())) {
                                                // get possible Type from
                                                // URL-String
                                                String newUrlType = urlCheck(newUrl.toString());
                                                Document doc = new Document("url", newUrl.toString());
                                                // save origin URL
                                                doc.put("origin", url);
                                                if (knowntypes.contains(newUrlType.trim())) {
                                                    doc.put(props.getProperty("formatField"), newUrlType);
                                                    newdatadb.getCollection("new_resources").insertOne(doc);
                                                }
                                                if (rdftypes.contains(newUrlType.trim())){
                                                    doc.put(props.getProperty("formatField"), newUrlType);
                                                    newdatadb.getCollection("new_rdf_resources").insertOne(doc);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            document.put("response_code", responseCode);
                            newdatadb.getCollection("Checked_Resources").insertOne(document);
                        }
                    } catch (UnknownHostException e) {
                        System.out.println("ERROR: " + "Unknown Host: " + e.getMessage());
                    } catch (SocketTimeoutException e) {
                        System.out.println("ERROR: " + "Timeout: " + e.getMessage());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                private List<String> crawl(InputStream is) throws IOException {
                    InputStreamReader isr = new InputStreamReader(is);
                    BufferedReader br = new BufferedReader(isr);
                    String line = null;
                    List<String> urls = new ArrayList<>();
                    do {
                        line = br.readLine();
                        if (line != null) {
                            if (line.trim().contains("href=\"")) {
                                String[] hrefSplits = line.trim().split("href=\"");
                                for (int i = 1; i < hrefSplits.length; i++) {
                                    line = hrefSplits[i].split("\"")[0];
                                    urls.add(line);
                                }
                            }
                        }
                    } while (line != null);
                    br.close();
                    return urls;
                }
            });
            client.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void disableSslVerification() {
        try {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }};

            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            // Install the all-trusting host verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
    }

    public static void manager(int start, int end, int threads, boolean auth) {

        Logger mongoLogger = Logger.getLogger("org.mongodb.driver");
        mongoLogger.setLevel(Level.SEVERE);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        int range = end - start;
        int threadRange = (int) Math.floor(range / threads);
        for (int i = start; i <= end; i = i + threadRange) {
            final int exeSkip = i;
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    worker(exeSkip, threadRange, auth);
                }
            });
        }
        pool.shutdown();
    }

    private static MongoClient clientInit() {
        String mongoClientURI = "mongodb://" + props.getProperty("dbuser") + ":" + props.getProperty("dbpassword") + "@" + props.getProperty("ip") + ":" + props.getProperty("port");
        MongoClientURI connectionString = new MongoClientURI(mongoClientURI);
        return new MongoClient(connectionString);
    }

    private static void generatePropertyFile(){
        Properties prop = new Properties();
        OutputStream output = null;

        try {

            output = new FileOutputStream("config.prop");

            // set the properties value
            prop.setProperty("ip", "localhost");
            prop.setProperty("port", "27017");
            prop.setProperty("auth", "false");
            prop.setProperty("dbuser", "admin");
            prop.setProperty("dbpassword", "");
            prop.setProperty("database", "RDFDump");
            prop.setProperty("collection", "Critical_URLs");
            prop.setProperty("urlField","downloadURL");
            prop.setProperty("formatField","format");
            prop.setProperty("timeout", "3000");
            prop.setProperty("startIndex", "0");
            prop.setProperty("endIndex", "1700000");
            prop.setProperty("steps", "50000");
            prop.setProperty("threads", "10");

            // save properties to project root folder
            prop.store(output, "HTMLCrawler Properties");

        } catch (IOException io) {
            io.printStackTrace();
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    private static Properties loadPropertyFile(){
        Properties prop = new Properties();
        InputStream input = null;

        try {

            input = new FileInputStream("config.prop");

            // load a properties file
            prop.load(input);

            return prop;
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }
    }

    public static void main(String[] args) {
        disableSslVerification();
        if(Arrays.asList(args).contains("-genprops"))
            generatePropertyFile();
        props = loadPropertyFile();
        if(props == null){
            generatePropertyFile();
            props = loadPropertyFile();
        }
        int steps = Integer.parseInt(props.getProperty("steps"));
        int startIndex = Integer.parseInt(props.getProperty("startIndex"));
        int endIndex = Integer.parseInt(props.getProperty("endIndex"));
        int threads = Integer.parseInt(props.getProperty("threads"));
        boolean auth =  Boolean.parseBoolean(props.getProperty("auth"));
        for(int i= startIndex; i < endIndex; i = i + steps){
            manager(i,i + steps, threads, auth);
        }
    }
}
