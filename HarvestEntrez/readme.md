# LiteratureHarvester

Creates sets of articles relevant to given disease (MeSH topic) from a given dated up to the moment of call.
In particular, for a given disease it creates three collections in a MongoDB database.
1. articles from PubMed with their abstract
2. articles from PubMed with their topics (MeSH descriptor ids)
3. articles from PMC with their full-text

## Requirements
java 1.8.0\_91

MongoDB v2.4.9

## How to use

The main method is in MainHarvester class and takes three arguments
 1. args[0]    path to settings YAML file 
 
### Configure
 Update the configurations in /settings.yaml
 
* baseFolder: The directory for indermediate files to be wrtitten 
* date 
    * from: Starting completion date after which to seek articles in  yyyy/mm/dd format (e.g. "2017/05/01")
    * to: Ending date completion date until which to seek articles in yyyy/mm/dd format (e.g. "2020/05/01")
* meshTerms: List of Mesh headings of diseases
* datasetId: An identifier used for naming resulting collections 
* mongodb: Details about the MongoDB database to be used to store the retrieved sets of articles with corresponding information.
    * host: The IP of the host (e.g. 127.0.0.1) 
    * port: The port (e.g. 27017)
    * dbname: The name of the Database (e.g. harvestingDb)
    
The output collections are created automatically in the given database based on the dataSetId and the provided dates with following formats: 'datasetId'\_'date/to'\_'date/from'\_pmc, 'datasetId'\_'date/to'\_'date/from'\_pubmed and 'datasetId'\_'date/to'\_'date/from'\_pubmed\_MeSH.
 
### Dependencies
All required libraries are listed in /dist/lib/Library Dependencies.txt

You should download them and put them in the /dist/lib/ before using HarvestEntrez.jar

### Run

A precompiled version is available in /dist/HarvestEntrez.jar

Example call:
```
> java -jar HarvestEntrez.jar "../settings.yaml"
```
