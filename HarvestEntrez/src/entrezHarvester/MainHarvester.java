/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package entrezHarvester;

import pubmedIndexing.PubmedArticlesIndexer;
import entrezSearcher.EntrezSearcher;
import help.Helper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import mongoConnect.MongoDatasetConnector;
import pmcParsing.PmcParser;
import yamlSettings.Settings;

/**
 * Used to Create Sets of articles relevant to given disease 
 * 
 * @author tasosnent
 */
public class MainHarvester {

    //Hardecoded values
    private final static boolean debugMode = true; //Enables printing of messages for normal functions
    private static String pathDelimiter = File.separator;    // The delimiter in this system (i.e. "\\" for Windows, "/" for Unix)
    
    private static Settings s; // The settings for the module
    
    String diseaseMeshID = "";  // MeSH id for the disease to searchNwrite for e.g. D009136 for DMD. Used for semantic searchNwrite.
    String dataSetID ="";            //ID of the current data Set, just for reference
    String query = "";              // the query to be searched
    String source = "";            // the datasource tp be searched
    // if this idList has a value, a PubmedSearcher By Ids will be used 
    String idList = null;             // list of pmids 
    
    String baseFolder = "";     // The folder to store files e.g. harvested XML and JSON output
    //Pubmed paths
    String xmlFolder = "";
    String indexfolder = "";
    String indexPath = "";
    String newPubmedIndexPath = "";
    String jsonFile = "";
    String jsonMeshFile = "";
    private MongoDatasetConnector mongoArticleDataSet; // An object to use for storage of harvested JSON article objects into MongoDB
    private MongoDatasetConnector mongoMeshRelationsDataSet; // An object to use for storage of harvested meshRelations JSON objects into MongoDB

    String logFile = "";

    Date start = null;
    Date end = null;
        
    /**
     * Create a Harvester
     *      default source is pubmed
     * @param dataSetID     An identifier for the specific data sets to be created
     * @param query         The query to be used for searchNwrite in the database
     */  
    public MainHarvester(String dataSetID, String query){
        this.dataSetID = dataSetID;
        this.query = query;
        this.source = "pubmed";
        createFolders();
    }    
    
    /**
     * Create a Harvester
     * @param dataSetID     An identifier for the specific data sets to be created
     * @param query         The query to be used for searchNwrite in the database
     * @param source        A list of IDs to restrict the query
     */
    public MainHarvester(String dataSetID, String query, String source){
        this.dataSetID = dataSetID;
        this.query = query;
        this.source = source;
        createFolders();
    }
    
    /**
     * Create a Harvester
     * @param dataSetID     An identifier for the specific data sets to be created
     * @param query         The query to be used for searchNwrite in the database
     * @param source        The data source to be searched (pubmed or pmc)
     * @param idList        A list of IDs to restrict the query
     */
    public MainHarvester(String dataSetID, String query, String source, String idList){
        this.dataSetID = dataSetID;
        this.query = query;
        this.source = source;
        this.idList = idList;
        createFolders();
    }
    
    /**
     * Creates a Data Set 
     * 
     *  1)  Calls EntrezSearcher to Search PubMed annotated articles with a specific MeSH heading
     *          Documents found are stored in XML files in folder: PrimaryXMLs
     *  2)  Calls PubmedArticlesIndexer to index downloaded documents in a Lucene index
     *          This call creates Lucene_index
     *  3)  Reads Lucene_index and writes a JSON file and/or lucene index with the selected fields.
     *          This program adds (again*) "has abstract" restriction to the data (other restrictions may be added too)
     *          The input-index should also contain those restrictions, as the XML file should be the result of the appropriate pubmedQuery. 
     *          
     *  e.g. settings.yaml
     *          >   java -jar entrezHarvester.jar settings.yaml
     * 
     * @param args
     *      args[0]     settings.yaml
     */
    public static void main(String[] args) {

        //Load settings from file
        String settingsFile;
        if(args.length == 1){ // command line call
            // TO DO add checks for these values
            settingsFile = args[0];
        } else { // hardcoded call with default settings file named settings.yaml available in the project main folder
            settingsFile = "." + pathDelimiter + "settings.yaml";
        }
        System.out.println(" " + new Date().toString() + " \t Creating data-set using settings file : " + args[0]);

        s = new Settings(settingsFile);
//        System.out.println(s.getProperty("baseFolder"));
        // get the last update setting
        String startDate = s.getProperty("date/from").toString();
        String endDate = s.getProperty("date/to").toString();
        String datasetId = s.getProperty("datasetId")
                + "_" + endDate.replaceAll("/", "_")
                + "_" + startDate.replaceAll("/", "_");

        ArrayList<String> meshTerms = (ArrayList<String>) s.getProperty("meshTerms");


        String meshTerm = String.join(" [MeSH Terms] OR ", meshTerms)
                + " [MeSH Terms]";

        // create query based on a MeSH term
            // This is a map so that it can be extensible :
            // If we want to test multiple queries, just put more elements in queries HahMap
        HashMap <String,String> queries = new HashMap <> ();

        queries.put("pubmed"," " + meshTerm
            + " AND ( hasabstract not hasretractionof not haserratumfor not haspartialretractionof )"
            + " AND ( " + startDate + "[Date - Completion] :" + endDate + "[Date - Completion]) " );
        queries.put("pmc"," " + meshTerm + " [MeSH Terms] "
//                + " AND ( hasabstract not hasretractionof not haserratumfor not haspartialretractionof )"
                + "AND (" + startDate + ":" + endDate + "[pmclivedate]) "
                + "AND open access[filter] AND cc license[filter]" );

        for(String source : queries.keySet()){
            // When testing multiple queries, add queryID in datasetID so that different folders will be created
            MainHarvester tsm = new MainHarvester(datasetId, queries.get(source),source);
            // Do steps to create dataSet(s)("Do not stop and wait before indexing", "Do not add extra fields that the official ones")
            tsm.doSteps(true);
        }
    }
    
    /**
     * Create data-set for current MainHarvester settings
     * 
     * @param extraFields   Whether to include extraFields (MeshUI) in JSON file. In general should be false.
     */
    public void doSteps( boolean extraFields){
        Date start = new Date();
//        System.err.println(" " + new Date().toString() + " \t Searching and Fetching... ");
        int results = search();
//        int results = 1;
//        System.err.println(" " + new Date().toString() + " \t Parsing and Indexing... ");
        if(results > 0){
            if(this.source.equals("pubmed")){
            indexPubmedDocuments();    
////        System.err.println(" " + new Date().toString() + " \t Selecting and Writing... ");
            createPubmedDataSet(extraFields);
            } else if(this.source.equals("pmc")){
                PmcParser pc = new PmcParser(this.baseFolder);
//              Write all in one JSON file may cause "out of memory exception"
                pc.xmlsToMongo(this.xmlFolder, this.mongoArticleDataSet);
//              Wirte in separate JSON files, memory safe for small "harvesting step"
//                pc.xmlsToJsons(this.xmlFolder, this.jsonFile.replace("JSON.json", "\\PMCjson"));
            }
        }
        Date end = new Date();
//        Helper.printTime(start, end, "creating data set " + dataSetID );
//        if(!this.jsonFile.equals("")){
//        System.err.println(" " + new Date().toString() + " \t " + this.dataSetID + " : \t " + Helper.getPmids(this.jsonFile).size() + " \t " + this.jsonFile);
//        }        
    }
  
    /** Step 0)
     * Create all folders and database connections needed 
     */
    private void createFolders(){
        //tmp code
        baseFolder = s.getProperty("baseFolder") + pathDelimiter + dataSetID + pathDelimiter + source + pathDelimiter; 
//        baseFolder = "." + pathDelimiter + dataSetID + pathDelimiter + source + pathDelimiter; 
        Helper.createFolder(baseFolder);
        //folder with primary XMLs files 
        xmlFolder = baseFolder + "XMLs"; 
        Helper.createFolder(xmlFolder);
        //folder with primary index
        indexfolder =  "Lucene_index";
        indexPath = baseFolder + indexfolder;
        // Files to export final data sets
        // TODO :  remove this newIndex, should not be used probably
        newPubmedIndexPath = baseFolder +"Lucene_index_selected";
        jsonFile = baseFolder + "JSON.json";                            

        //MongoDB connection
        String host = s.getProperty("mongodb/host").toString();
        int port = (Integer)s.getProperty("mongodb/port");
        String dbName = s.getProperty("mongodb/dbname").toString();        
        mongoArticleDataSet = new MongoDatasetConnector(host, port,dbName,dataSetID + "_" + source);
        //For PubMed create addtional dataset for Mesh relations harvested
        if(this.source.equals("pubmed")){
            mongoMeshRelationsDataSet = new MongoDatasetConnector(host, port,dbName,dataSetID + "_" + source + "_MeSH");
            jsonMeshFile = baseFolder + "MESH.json";                            
        }
        // Redirect output to Log file 
//        logFile = baseFolder + "log.txt";
//        try {
//            System.setOut(new PrintStream(logFile));
//        } catch (FileNotFoundException ex) {
//            System.out.println(" " + new Date().toString() + " problem setting " + logFile + " as log file : " + ex.getMessage());
//        }
    }

    /** Step 1)
     * Search PubMed or PMC and write hits 
     *  
     * @return  Number of elements matching the query
     */
    public int search(){
        int dataElementsfound = 0;
        // Search for pubmed or pmc
        EntrezSearcher pc;
        pc = new EntrezSearcher(this.source, "xml", query, xmlFolder); 
        start = new Date();
        pc.search();
        dataElementsfound = pc.getCount();
        if(dataElementsfound > 0){
            pc.fetch();
        }
        end = new Date();
        Helper.printTime(start,end, "Searching " + this.source); 
        return dataElementsfound;
    }
    
    /** Step 2)
     * Index documents into a lucene index
     */
    public void indexPubmedDocuments(){
        PubmedArticlesIndexer tdi = new PubmedArticlesIndexer();
        //Parsing XML File with SAX - event orientent way 
        try{
            //log printing
            System.out.println(" " + new Date().toString() + " indexPath : " + indexPath);                                                                                      
             start = new Date();
                tdi.indexDocs(xmlFolder, indexPath);
             end = new Date();
            Helper.printTime(start, end,"indexing"); 
        } catch (IOException e) {
            //log printing
            System.out.println(" " + new Date().toString() + " caught a (indexing) " + e.getClass() + " with message: " + e.getMessage());
        }  
    }
    
    /** Step 3)
     * Create test data files (JSON file and/or index)
     * @param extraFields whether to include extra fields (publication year and grant) in JSON file or not. For official test sets is normally false.
     */
    public void createPubmedDataSet(boolean extraFields){
        //Restrictions: Only documents with abstract and without DescriptorName should be included to test data
            String query = "+AbstractText:[\\\"\\\" TO *] ";

        dataWriter searcher;
        try {
            searcher = new dataWriter(indexPath,null,null,null,mongoArticleDataSet,mongoMeshRelationsDataSet,extraFields);
            start = new Date();
            try{
                searcher.searchNwrite(query);
            } catch (Exception e) {
                System.out.println(" caught a (searcher.Search) " + e.getClass() + "\n with message: " + e.getMessage());
//                e.printStackTrace();
            }
            end = new Date();
            Helper.printTime(start, end,"Creating Test Data"); 
        } catch (IOException ex) {
                System.out.println(" caught a (searcher.Search) " + ex.getClass() + "\n with message: " + ex.getMessage());
//                ex.printStackTrace();
        }
            
    }

}
