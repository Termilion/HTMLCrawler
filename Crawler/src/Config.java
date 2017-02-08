import java.util.Arrays;
import java.util.List;

public class Config {
    public static final String ip = "localhost";
    public static final int port = 27017;

    public static final String username = "admin";
    public static final String userpw = "swjltv1409";

    public static final String db = "RDFDump";
    public static final String collection = "Critical_URLs";

    public static final String urlField = "downloadURL";
    public static final String formatField = "format";
    public static final String catalogField = "ckanCatalog";

    //  7470403255

    public static final int timeout = 3000;

    public static final List<String> knowntypes = Arrays.asList("csv", "avi", "mp4", "xsd", "xlsx", "rdf", "json", "pdf",
            "xml", "xls", "php", "geojson", "zip", "rar", "txt", "tsv", "tgz", "jpg", "png", "docx", "doc", "odt",
            "ods", "7z", "py", "js", "epub", "rtf", "xlsm", "xsl", "wfsx", "gif", "gml32", "cgi", "nsf", "kmz",
            "kml+xml", "rss", "gml3", "gml2", "gml31", "rss+xml", "gmz", "xsp", "shape-zip", "gml", "gtif", "wfs",
            "tfw", "aspx", "map", "jsp", "shtml", "n3", "tif", "gz", "jp2", "wmv", "ppt", "pptx", "ttl", "kml", "wms",
            "nc", "turtle", "summary", "gpkg", "jpw", "exe", "jsonld", "jsf", "asp", "shp", "kmz xml", "cfm", "tar",
            "tcl", "svc", "sdmx", "n3", "ntriples", "rdf+xml", "nt", "nq", "nquads", "trig", "trix", "rdfs", "");

    public static final List<String> rdftypes = Arrays.asList("n3", "ntriples", "nc", "rdf+xml", "rdfs", "xml", "xls", "xsd", "rdf", "nt", "nq", "nquads", "trig", "trix", "json", "ttl", "turtle", "jsonld");
}