# HTML-Crawler
## Description
* Search for new RDF and archive resources in HTML-resources.
* Filters a MongoDB Dataset for URLs that could possibly lead to HTML-Files, then stores all the URLs that have a common Datatype in a seperate Database.
* Returns a JSONArray Object cotaining Metadata about the result.

**Example Output:**
```
[{
	"total":3613327,
	"filtered":1747075,
	"responded":44649,
	"fileServers":1089,
	"newResources":70940
},{
	"gz_newResources":0,"gz_origins":0,
	"zip_newResources":39387,"zip_origins":52,
	"tar_newResources":0,"tar_origins":0,
	"rar_newResources":1,"rar_origins":1,
	"tgz_newResources":0,"tgz_origins":0
},{
	"xml_newResources":285,"xml_origins":57,
	"rdf_newResources":19,"rdf_origins":9,
	"ttl_newResources":1,"ttl_origins":1,
	"n3_newResources":1,"n3_origins":1,
	"nt_newResources":0,"nt_origins":0,
	"nq_newResources":0,"nq_origins":0,
	"owl_newResources":0,"owl_origins":0
}]
```

## Dependencies
* MongoDB Java Driver version 3.3.0
* Apache Commons Validator version 1.5.1
* JSON

**For Maven:**
```
<dependencies>
  <!-- https://mvnrepository.com/artifact/commons-validator/commons-validator -->
  <dependency>
    <groupId>commons-validator</groupId>
    <artifactId>commons-validator</artifactId>
    <version>1.5.1</version>
  </dependency>
  <dependency>
    <groupId>org.mongodb</groupId>
    <artifactId>mongodb-driver</artifactId>
    <version>3.3.0</version>
  </dependency>
  <!-- https://mvnrepository.com/artifact/org.json/json -->
  <dependency>
    <groupId>org.json</groupId>
    <artifactId>json</artifactId>
    <version>20160212</version>
  </dependency>
</dependencies>
```

## Properties File
User Input is handled with a Properties File.
```
#Crawler Properties
#Tue Feb 28 13:59:30 CET 2017
ip=localhost
port=27017
auth=true
dbuser=
dbpassword=
database=					# Database that contains the data
collection=					# Name of the Collection containing the unfiltered data
urlField=downloadURL		# Field that contains the URL
startIndex=0				# Skip to this Index
endIndex=					# Last Index to be Crawled
maxTime=4320				# Maximum Execution Timeout (in minutes)
timeout=4000				# Connection Timeout (in milliseconds)
threads=50					# Number of Threads
```

## Usage

* For the basic use you only need to run the run() Method.
* The first Parameter is the (local) Path to the Properties File
* The second Parameter is a boolean that decides if it runs the filter method first.
* **!!** The filter method is only optional **after** it was run once **!!**

```
import org.htmlCrawler.*;
...
HTMLCrawler crawler = new HTMLCrawler();
JSONArray json = crawler.run("./config.prop", true);
...
```
