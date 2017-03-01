package org.htmlCrawler;

import com.mongodb.Block;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.commons.validator.routines.UrlValidator;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Robert Bielinski
 */
public class HTMLCrawler {

    /**
     * Logger for output
     */
    private static final Logger log = Logger.getLogger(HTMLCrawler.class.getName());

    /**
     * List filled with common Data Types
     */
    private static final List<String> knowntypes = Arrays.asList("csv", "avi", "mp4", "xsd", "xlsx", "rdf", "json", "pdf",
            "xml", "xls", "php", "geojson", "zip", "rar", "txt", "tsv", "tgz", "jpg", "png", "docx", "doc", "odt",
            "ods", "7z", "py", "js", "epub", "rtf", "xlsm", "xsl", "wfsx", "gif", "gml32", "cgi", "nsf", "kmz",
            "kml+xml", "rss", "gml3", "gml2", "gml31", "rss+xml", "gmz", "xsp", "shape-zip", "gml", "gtif", "wfs",
            "tfw", "aspx", "map", "jsp", "shtml", "n3", "tif", "gz", "jp2", "wmv", "ppt", "pptx", "ttl", "kml", "wms",
            "nc", "turtle", "summary", "gpkg", "jpw", "exe", "jsonld", "jsf", "asp", "shp", "kmz xml", "cfm", "tar",
            "tcl", "svc", "sdmx", "n3", "ntriples", "rdf+xml", "nt", "nq", "nquads", "trig", "trix", "rdfs", "");

    /**
     * List filled with RDF Data-Types
     */
    private static final List<String> rdftypes = Arrays.asList("n3", "ntriples", "nc", "rdf+xml", "rdfs", "xml", "xls", "xsd", "rdf", "nt", "nq", "nquads", "trig", "trix", "json", "ttl", "turtle", "jsonld");

    private Hashtable<String, String> props;

    /**
     * Constructor
     */
    public HTMLCrawler() {
        log.setLevel(Level.INFO);
    }

    /**
     * Returns the possible Data-Type of the URL destination
     *
     * @param url URL
     * @return Type of the URL
     */
    public String urlCheck(String url) {
        try {
            String[] split = url.split("\\.");
            String urltype = split[split.length - 1];
            urltype = urltype.split("\\?")[0];
            urltype = urltype.toLowerCase();
            if (urltype.contains("format="))
                urltype = urltype.split("format=")[1];
            if (urltype.contains("#"))
                urltype = urltype.split("#")[0];
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

    /**
     * get the HTTP-Responsecode
     *
     * @param url URL
     * @return HTTP-Responsecode
     * @throws MalformedURLException URL Malformed
     * @throws IOException           IOException
     */
    private static int getResponseCode(String url) throws IOException {
        URLConnection con = new URL(url).openConnection();
        HttpURLConnection http = (HttpURLConnection) con;
        return http.getResponseCode();
    }

    /**
     * Crawls through URL-Resources and fills a new collection with all resources that could be HTML
     *
     * @param client MongoDB Client
     */
    public void filterResources(MongoClient client) {
        MongoDatabase database = client.getDatabase(props.get("database"));
        MongoCollection<Document> collection = database.getCollection(props.get("collection"));
        MongoDatabase newdatadb = client.getDatabase("HTMLCrawler");
        FindIterable<Document> it = collection.find().noCursorTimeout(true);
        it.forEach(new Block<Document>() {
            @Override
            public void apply(Document document) {
                if (document != null) {
                    String url = (String) document.get(props.get("urlField"));
                    if (url != null) {
                        String type = urlCheck(url);
                        if (!knowntypes.contains(type.trim())) {
                            newdatadb.getCollection("filtered_data").insertOne(document);
                        }
                    }
                }
            }
        });
    }

    /**
     * Worker Class, Iterates over Collection, normalizes the datatype field and crawls through every HTML resource to retrieve new resources
     *
     * @param client MongoDB Client
     * @param skip   Number of Resources that should be skipped
     * @param limit  Max Number of Resources to check
     */
    private void worker(MongoClient client, int skip, int limit) {
        try {
            MongoDatabase newdatadb = client.getDatabase("HTMLCrawler");
            MongoCollection<Document> collection = newdatadb.getCollection("filtered_data");
            String[] schemes = {"http", "https"};
            UrlValidator urlValidator = new UrlValidator(schemes);
            FindIterable<Document> it = collection.find().noCursorTimeout(true).skip(skip).limit(limit);
            it.forEach(new Block<Document>() {
                @Override
                public void apply(Document document) {
                    try {
                        // get url
                        String url = document.getString(props.get("urlField"));
                        String realType;
                        int responseCode;
                        // connect
                        if (url != null && urlValidator.isValid(url)) {
                            URL u = new URL(url);
                            HttpURLConnection http = (HttpURLConnection) u.openConnection();
                            http.setReadTimeout(3000);
                            http.setConnectTimeout(Integer.parseInt(props.get("timeout")));
                            // get content, type & response code
                            responseCode = http.getResponseCode();
                            if (responseCode >= 200 && responseCode <= 299) {
                                // Resource is OK! -> go on.
                                InputStream is = (InputStream) http.getContent();
                                // get normalized Content Type
                                realType = http.getContentType();
                                document.put("normalized_format", realType);
                                if (realType.contains("text/html")) {
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
                                                    doc.put("format", newUrlType);
                                                    newdatadb.getCollection("new_resources").insertOne(doc);
                                                }
                                                if (rdftypes.contains(newUrlType.trim())) {
                                                    doc.put("format", newUrlType);
                                                    newdatadb.getCollection("new_rdf_resources").insertOne(doc);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            document.put("response_code", responseCode);
                            newdatadb.getCollection("checked_resources").insertOne(document);
                        }
                    } catch (UnknownHostException e) {
                        log.fine("ERROR: " + "Unknown Host: " + e.getMessage());
                    } catch (SocketTimeoutException e) {
                        log.fine("ERROR: " + "Timeout: " + e.getMessage());
                    } catch (ProtocolException e) {
                        log.fine("ERROR: " + "Unknown Protocol: " + e.getMessage());
                    } catch (ClassCastException e) {
                        log.fine("ERROR: " + "ClassCastExeption: " + e.getMessage());
                    } catch (MalformedURLException | URISyntaxException e) {
                        log.fine("ERROR: " + "URL Exception: " + e.getMessage());
                    } catch (ConnectException e) {
                        log.fine("ERROR: " + "ConnectionException: " + e.getMessage());
                    } catch (Exception e) {
                        log.fine(e.getMessage());
                    }
                }

                private List<String> crawl(InputStream is) throws IOException {
                    InputStreamReader isr = new InputStreamReader(is);
                    BufferedReader br = new BufferedReader(isr);
                    String line;
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Disables the SSL Verification, because it throws errors on our resources.
     */
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
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    /**
     * Manager Class, Manages Thread-Pool and Thread-Timeout
     *
     * @param client     MongoDB Client
     * @param start      Start Index
     * @param end        End Index
     * @param threads    Number of Threads
     * @param maxTimeout Timeout for the Thread-Pool in minutes
     */
    private void manager(MongoClient client, long start, long end, int threads, int maxTimeout) {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        double range = end - start;
        double td = (double) threads;
        int threadRange = (int) Math.ceil(range / td);
        for (long i = start; i < end; i = i + threadRange) {
            final int exeSkip = (int) i;
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    worker(client, exeSkip, threadRange);
                }
            });
        }
        pool.shutdown();
        try {
            if (!pool.awaitTermination(maxTimeout, TimeUnit.MINUTES)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.severe(e.getMessage());
            pool.shutdownNow();
        }
    }

    /**
     * Initializes MongoDB Client with Authentication
     *
     * @return MongoDB Client
     */
    private MongoClient clientInit() {
        String mongoClientURI = "mongodb://" + props.get("dbuser") + ":" + props.get("dbpassword") + "@" + props.get("ip") + ":" + props.get("port");
        MongoClientURI connectionString = new MongoClientURI(mongoClientURI);
        return new MongoClient(connectionString);
    }

    /**
     * Generates Properties File
     */
    private void generatePropertyFile() {
        Properties prop = new Properties();
        OutputStream output = null;

        try {

            output = new FileOutputStream("config.prop");
            prop.store(output, "Crawler Properties");

            // set the properties value
            prop.setProperty("ip", "localhost");
            prop.setProperty("port", "27017");
            prop.setProperty("auth", "false");
            prop.setProperty("dbuser", "admin");
            prop.setProperty("dbpassword", "");
            prop.setProperty("database", "");
            prop.setProperty("collection", "");
            prop.setProperty("urlField", "downloadURL");
            prop.setProperty("startIndex", "0");
            prop.setProperty("endIndex", "");
            prop.setProperty("timeout", "3000");
            prop.setProperty("threads", "50");
            prop.setProperty("maxTime", "4320");

            // save properties to project root folder
            prop.store(output, "Crawler Properties");

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

    /**
     * Loads Properties File
     *
     * @param pathToConfig Path to PropertiesFile
     */
    private void loadPropertyFile(String pathToConfig) {
        Properties prop = new Properties();
        InputStream input;
        Hashtable<String, String> properties = new Hashtable<>();
        try {
            File file = new File(pathToConfig);
            input = new FileInputStream(file);

            // load a properties file
            prop.load(input);
            properties.put("ip", prop.getProperty("ip"));
            properties.put("port", prop.getProperty("port"));
            properties.put("auth", prop.getProperty("auth"));
            properties.put("dbuser", prop.getProperty("dbuser"));
            properties.put("dbpassword", prop.getProperty("dbpassword"));
            properties.put("database", prop.getProperty("database"));
            properties.put("collection", prop.getProperty("collection"));
            properties.put("urlField", prop.getProperty("urlField"));
            properties.put("timeout", prop.getProperty("timeout"));
            properties.put("startIndex", prop.getProperty("startIndex"));
            properties.put("endIndex", prop.getProperty("endIndex"));
            properties.put("threads", prop.getProperty("threads"));
            properties.put("maxTime", prop.getProperty("maxTime"));

            input.close();
            props = properties;
        } catch (IOException ex) {
            ex.printStackTrace();
            props = properties;
        }
    }

    /**
     * Loads PropertiesFile from standard location
     */
    private void loadPropertyFile() {
        loadPropertyFile("./config.prop");
    }

    /**
     * Runs MongoDB Queries to get the desired data and returns them in a JSONArray as follows:
     * [0]: General Metadata
     * [1]: Numbers of Archive-Files
     * [2]: Numbers of RDF-Files
     *
     * @param client MongoDB Client
     * @return JSONArray
     */
    public JSONArray gatherData(MongoClient client) {
        JSONArray jsonArray = new JSONArray();
        JSONObject json = new JSONObject();
        String dbName = props.get("database");
        String colName = props.get("collection");
        List<String> archiveTypes = Arrays.asList("gz", "zip", "tar", "rar", "tgz");
        List<String> rdfTypes = Arrays.asList("rdf", "n3", "ttl", "xml", "owl", "nq", "nt");
        MongoCollection coll = client.getDatabase("HTMLCrawler").getCollection("new_resources");
        MongoCollection dump = client.getDatabase(dbName).getCollection(colName);
        MongoCollection filter = client.getDatabase("HTMLCrawler").getCollection("filtered_data");
        MongoCollection checked = client.getDatabase("HTMLCrawler").getCollection("checked_resources");
        // general numbers
        long total, filtered, responded, fileservers, newResources;
        //gather data
        total = dump.count();
        filtered = filter.count();
        responded = checked.count();
        AggregateIterable<Document> totalServers = coll.aggregate(Arrays.asList(
                new Document("$group", new Document("_id", "$origin").append("resources", new Document("$sum", 1))),
                new Document("$group", new Document("_id", null).append("total", new Document("$sum", 1))),
                new Document("$project", new Document("_id", 0).append("total", 1))
        ));
        fileservers = 0;
        if (totalServers.first() != null) {
            fileservers = (int) totalServers.first().get("total");
        }
        newResources = coll.count();
        //add data
        json.put("total", total);
        json.put("filtered", filtered);
        json.put("responded", responded);
        json.put("fileServers", fileservers);
        json.put("newResources", newResources);
        jsonArray.put(json);
        json = new JSONObject();
        // archive numbers
        for (String type : archiveTypes) {
            long servers, newRes;
            //gather data for type
            AggregateIterable<Document> serverCounter = coll.aggregate(Arrays.asList(
                    new Document("$match", new Document("format", type)),
                    new Document("$group", new Document("_id", "$origin").append("resources", new Document("$sum", 1))),
                    new Document("$group", new Document("_id", null).append("total", new Document("$sum", 1))),
                    new Document("$project", new Document("_id", 0).append("total", 1))
            ));
            servers = 0;
            if (serverCounter.first() != null) {
                servers = (int) serverCounter.first().get("total");
            }
            newRes = coll.count(new Document("format", type));
            //add data
            json.put(type + "_origins", servers);
            json.put(type + "_newResources", newRes);
        }
        jsonArray.put(json);
        json = new JSONObject();
        // rdf numbers
        for (String type : rdfTypes) {
            long servers, newRes;
            //gather data for type
            AggregateIterable<Document> serverCounter = coll.aggregate(Arrays.asList(
                    new Document("$match", new Document("format", type)),
                    new Document("$group", new Document("_id", "$origin").append("resources", new Document("$sum", 1))),
                    new Document("$group", new Document("_id", null).append("total", new Document("$sum", 1))),
                    new Document("$project", new Document("_id", 0).append("total", 1))
            ));
            servers = 0;
            if (serverCounter.first() != null) {
                servers = (int) serverCounter.first().get("total");
            }
            newRes = coll.count(new Document("format", type));
            //add data
            json.put(type + "_origins", servers);
            json.put(type + "_newResources", newRes);
        }
        jsonArray.put(json);
        return jsonArray;
    }

    /**
     * Full Run of the Tool. Filtering, Craling and gathering the Data.
     * Returns JSONArray with this data:
     * [0]: General Metadata
     * [1]: Numbers of Archive-Files
     * [2]: Numbers of RDF-Files
     *
     * @param pathToConfig Path to the Config File
     * @param filter       set to false if you want to reuse an old filtered_data collection
     */
    public JSONArray run(String pathToConfig, boolean filter) {
        disableSslVerification();
        loadPropertyFile(pathToConfig);

        if (props == null) {
            generatePropertyFile();
            log.severe("Config file could not be loaded, generated new basic config: ./config.prop");
            return null;
        } else {
            log.info("Config file loaded");
            boolean auth = Boolean.parseBoolean(props.get("auth"));
            MongoClient client;
            if (auth) {
                client = clientInit();
            } else client = new MongoClient(props.get("ip"), Integer.parseInt(props.get("port")));

            if (filter) {
                //clean up all generated Collections
                cleandb(client);
                log.info("Filtering data for potential HTML-resources to check");
                filterResources(client);
            } else {
                //clean up all generated Collections except filtered_data
                cleancol(client);
            }

            log.info("Crawling through HTML-resources");
            manager(client, Long.parseLong(props.get("startIndex")), Long.parseLong(props.get("endIndex")), Integer.parseInt(props.get("threads")), Integer.parseInt(props.get("maxTime")));

            log.info("Gathering data");
            JSONArray result = gatherData(client);
            log.info("Done!");
            client.close();
            return result;
        }
    }

    /**
     * Drops Generated DB
     *
     * @param client MongoDB Client
     */
    public void cleandb(MongoClient client) {
        client.getDatabase("HTMLCrawler").drop();
    }

    /**
     * Drops checked_resources and new_resources
     *
     * @param client MongoDB Client
     */
    public void cleancol(MongoClient client) {
        client.getDatabase("HTMLCrawler").getCollection("checked_resources").drop();
        client.getDatabase("HTMLCrawler").getCollection("new_resources").drop();
    }

    /**
     * Main Method for testing purposes
     *
     * @param args Arguments
     */
    public static void main(String[] args) {
        Logger mongoLogger = Logger.getLogger("org.mongodb.driver");
        mongoLogger.setLevel(Level.SEVERE);
        HTMLCrawler crawler = new HTMLCrawler();
        if (Arrays.asList(args).contains("-genprops"))
            crawler.generatePropertyFile();
        else {
            System.out.println(crawler.run("./config.prop", false));
        }
    }
}
