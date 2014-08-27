package gpii;

import gpii.schemas.UPREFS;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;

import org.json.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jena.atlas.lib.Bytes;
import org.apache.jena.riot.RDFDataMgr;

import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.jena.JenaJSONLD;
import com.github.jsonldjava.utils.JSONUtils;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFormatter;
import com.hp.hpl.jena.rdf.model.InfModel;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.reasoner.rulesys.GenericRuleReasoner;
import com.hp.hpl.jena.reasoner.rulesys.Rule;
import com.hp.hpl.jena.shared.DoesNotExistException;

public class PrototypeRBMM {

	private static final Log LOG = LogFactory.getLog(PrototypeRBMM.class);
	
	// default model automatically initialized with data from JSON-LD  	
	public static Model m;
	
	//	accessibilityConflictModel
	public static Model acm;
	
	public static BufferedReader br;
	
	// JenaJSONLD must be initialized so that the readers and writers are registered with Jena. 
	static {
	    JenaJSONLD.init();       
	}

	public static void main(String[] args) throws IOException {
		LOG.info("START");	
				
		m = ModelFactory.createDefaultModel();

		String in = "";
		while (!in.equals("Q")) {
			try {
				in = getUserInput();
			} catch (DoesNotExistException e) {
				in = "";
			}
			try {
				execute(in);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public static String getUserInput() throws IOException {
		br = new BufferedReader(new InputStreamReader(System.in));
		String input = null;

		System.out.println("Please enter a command");
		// pre-processing
		System.out.println("[0] Pre-processing - add context to preferences and device characteristics and store as JSONLD");
		// model functions 
		System.out.println("[1] Load preferences (JSONLD) and device characteristics (JSONLD)");
		System.out.println("[2] Load other semantic data source (registry and solutions) ");
		System.out.println("[3] Run JENA rules to infer knowledge used for conflict resolution");
		// not nerssesary anymore 
		System.out.println("[4] SPARQL Query: detect conflicts");		
		System.out.println("[5] Resolve confilcts");
		
		// to revise the output 
		System.out.println("[6] Output: Results as JSONLD object");

		// Helper functions 
		System.out.println("[7] Print all statements of model m");
		System.out.println("[8] Print number of statements of model m");
		
		System.out.println("[Q] Quit");

		input = br.readLine();

		return input;
	}
	
	/** 
	 * pre-processing of preferences  
	 * input: JSON object - same as always (see test files in gpii.testData.preferences)
	 * pre-processing: 
	 * (1) Transforms the original preference set to the request format (gpii.testdData.input.preferences.jsonld) 
	 * (2) Transforms the original solution object to the requested format (gpii.testdData.input.solutions.jsonld)			 * 
	 */
	public static void execute(String command) throws IOException, JSONException, URISyntaxException {
		
		/** 
		 * TODO this are static input source used in the RBMM Web Service.
		 * These input sources should be exchangeable with any other semantic representations of preference terms or solutions
		 */
		
		// Define semantic representations: 
	    // String reg = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\semantics\\registry.jsonld";
	    String semanticsSolutions = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\semantics\\semanticsSolutions.jsonld";
	    String explodePrefTerms = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\semantics\\explodePreferenceTerms.jsonld";
	    
	    
		// Final input format for the reasoning process. Created in [0].  
		String preferenceInput = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\testData\\input\\preferences.jsonld";
		String solutionsInput = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\testData\\input\\solutions.jsonld";
		
		if (command.equals("0")){
			LOG.info("Pre-processing - add context to preferences and device characteristics and store as JSONLD");		
			/**
			 * Original preferences sets and solutions- test files for multiple solution conflict resolution
			 * TODO automated tests 
			 */
			// solution test file for multiple solutions conflict:
			String deviceFile = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\testData\\installedSolutions\\multipleMagnifierScreenreader.json";
			
			// 1. preferences test files for multiple solution conflict: 
			// 1.1 Test: nothing preferred => resolution is to launch on randomly 
			//String preferenceFile = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\testData\\preferences\\noAppSpecificPrefs.json";
			
			// 1.2 Test: one single solution is preferred => resolution is to launch the preferred 
			//String preferenceFile = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\testData\\preferences\\singleAppSpecificPrefs.json";
			
			// 1.3 Test: one single solution is preferred => resolution is to launch the preferred 
			//String preferenceFile = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\testData\\preferences\\multipleAppSpecificPrefs.json";
			
			// 2. preferences test files for Abstract Preferences Conflict
			// 2.1 Test: pointerControllEnhancement = visibility 
			String preferenceFile = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\testData\\preferences\\pointerControllEnhancement.json";
			
			// Transforming preferences: 
			String preferenceString = readFile(preferenceFile, StandardCharsets.UTF_8);
			JSONTokener preferencesTokener = new JSONTokener(preferenceString);
			JSONObject preferences = new JSONObject(preferencesTokener);		
			
			JSONObject outerPrefsObject = new JSONObject();

			JSONArray prefsArray = new JSONArray();

	        Iterator<?> keys = preferences.keys();
	        while( keys.hasNext() ){
	            String key = (String)keys.next();
	            /**
	             * create a new inner JSONobject and put: 
	             * @id = URI
	             * gpii:type = common or application
	             * gpii:name = preference name
	             * gpii:value = either the value (common) or an JSONObject of values (app-specific)   
	             */ 
	            JSONObject innerPrefsObject = new JSONObject();
	            innerPrefsObject.put("@id", key);
	            if (key.contains("common")) innerPrefsObject.put(UPREFS.type.toString(), "common"); 
	            if (key.contains("applications")) innerPrefsObject.put(UPREFS.type.toString(), "applications");
	            URI uri = new URI(key);
	            String path = uri.getPath();
	            String idStr = path.substring(path.lastIndexOf('/') + 1);
	            innerPrefsObject.put(UPREFS.name.toString(), idStr);
	            // transform values to gpii:value: 
	            if( preferences.get(key) instanceof JSONArray ){
	            		// outer value array	        
	            		JSONArray values = new JSONArray(preferences.get(key).toString());
	        	        for (int i = 0, size = (values.length()); i < size; i++)
	        		    {	
	        	        	// inner value object
	        	        	innerPrefsObject = getPreferenceValues(innerPrefsObject, values.get(i), key);
	        		    }
	            }
	            prefsArray.put(innerPrefsObject);	            
	        }
	        //LOG.info(prefsArray);
	        outerPrefsObject.put(UPREFS.preference.toString(), prefsArray);
		    byte dataToWrite[] = outerPrefsObject.toString().getBytes(StandardCharsets.US_ASCII);
		    writeFile(preferenceInput, dataToWrite);
		    
			// Transforming solutions: 
			JSONObject solutions = new JSONObject(); 
			String deviceString = readFile(deviceFile, StandardCharsets.UTF_8);
			JSONTokener deviceTokener = new JSONTokener(deviceString);
			JSONArray device = new JSONArray(deviceTokener);			
		
			JSONArray sol = new JSONArray(); 
		    for (int i = 0, size = device.length(); i < size; i++)
		    {
		      JSONObject objectInArray = device.getJSONObject(i);
		      String[] elementNames = JSONObject.getNames(objectInArray);
		      for (String elementName : elementNames)
		      {
		        String value = objectInArray.getString(elementName);
		        sol.put(value); 
		      }
		    }
		    solutions.put(UPREFS.installedSolutions.toString(), sol);
		    byte cDataToWrite[] = solutions.toString().getBytes(StandardCharsets.US_ASCII);
		    writeFile(solutionsInput, cDataToWrite);
		    
		}
		else if (command.equals("1")) {
			
			LOG.info("Load preferences (JSONLD) and device characteristics (JSONLD)");
			
			// load accessibility namespace
			m.setNsPrefix("ax", UPREFS.NS);
			// create RDF Model from preferences and solutions
			m = ModelFactory.createDefaultModel().read(preferenceInput, "JSON-LD");
			Model d = ModelFactory.createDefaultModel().read(solutionsInput, "JSON-LD");
			m.add(d);
			//m.write(System.out);

			// TODO: use ModelFactors or RDFDataMrg ? 
			//alternative to read preferences from JSONLD			
			//RDFDataMgr.read(m, inputURL.toUri().toString(), null, JenaJSONLD.JSONLD);
			 
		}
		else if (command.equals("2")) {
			LOG.info("Load other semantic data source (registry and solutions)");

		    //Model registry = ModelFactory.createDefaultModel().read(reg, "JSON-LD");
	        Model uListing = ModelFactory.createDefaultModel().read(semanticsSolutions, "JSON-LD");
	        Model exTerms = ModelFactory.createDefaultModel().read(explodePrefTerms, "JSON-LD");
	        
	        // merge the Models
	        m = m.union(exTerms);
	        //m = m.union(uListing);	        
	        // print the Model as RDF/XML
	        m.write(System.out);
		}
		else if (command.equals("3")) {
			LOG.info("Run JENA rules to infer knowledge used for conflict resolution");
			/** 
			 * TODO make this mapping more general to achieve the goal that we are not limited to GPII input sources
			 * This step is used for any kind of mappings from an abritary input source (here preferences from GPII) 
			 * to infer required knowledge for the RBMM reasoning and vocabulary. 
			 * 
			 */
		    String mappingRules = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\testData\\rules\\mappingRules.rules";

		    File f = new File(mappingRules);
			if (f.exists()) {
				List<Rule> rules = Rule.rulesFromURL("file:" + mappingRules);
				GenericRuleReasoner r = new GenericRuleReasoner(rules);
				InfModel infModel = ModelFactory.createInfModel(r, m);
			      // starting the rule execution
			     infModel.prepare();					
			    // TODO why am I doing this here?
			    Model deducedModel = infModel.getDeductionsModel();  
				m.add(deducedModel);
				deducedModel.write(System.out);
				//m.write(System.out);
			} else
				System.out.println("That rules file does not exist.");
		} 
		else if (command.equals("4")){
			LOG.info("SPARQL Query: detect conflicts");
		    String multSolConflictFile = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\detectMultipleSolutionConflict.sparql";
		    String noSolConflictFile = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\detectNoSolutionConflict.sparql";
		    		    
		    String[] queries = {readFile(multSolConflictFile, StandardCharsets.UTF_8), readFile(noSolConflictFile, StandardCharsets.UTF_8)}; 
	        // Run each query, then show its individual results, and add
	        // them to the combined model
	        for ( String q : queries ) {
	            Query query = QueryFactory.create(q) ;
				QueryExecution qexec = QueryExecutionFactory.create(query, m) ;
				acm = qexec.execConstruct();
				/* output results  for testing 
				System.out.println( "\n<!-- results of: " +query+" -->" );
	            acm.write( System.out, "RDF/XML-ABBREV" );
	            */
	            m.add(acm);
	            m.write(System.out); 
				qexec.close();	
	        }			  
	
		}
		else if (command.equals("5")){
			LOG.info("Resolve confilcts");
			/**
			 * TODO implement conflict resolution
			 */
		    String queryStringFile = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\resolveMultipleSolutionConflict.sparql";
		    String queryString = readFile(queryStringFile, StandardCharsets.UTF_8); 
		    Query query = QueryFactory.create(queryString) ;
			QueryExecution qexec = QueryExecutionFactory.create(query, m) ;
			acm = qexec.execConstruct() ;
			System.out.println( "\n<!-- results of: " +query+" -->" );
            acm.write( System.out, "RDF/XML-ABBREV" );
			m.add(acm);
			//m.write(System.out);
		    
		}		
		else if (command.equals("6")) {
			LOG.info("Output: Results as JSONLD object");
			/**
			 * TODO implement output as JSONLD
			 */
			System.out.println("still not implemented"); 
			
			// Test 1 - use RDFDataMrg 
			// RDFDataMgr.write(System.out, (Model) m, JenaJSONLD.JSONLD);
			// m.write(System.out, "JSON-LD");
			
			// TEST 2 - use JSONLdProcessor
			/*JsonLdOptions options = new JsonLdOptions(); 
		    options.format = "application/ld+json";		    
		    Object json;
			try {
				json = JsonLdProcessor.fromRDF(m, options);
			    String jsonStr = JSONUtils.toPrettyString(json);
			    System.out.println(jsonStr);				
			} catch (JsonLdError e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}*/
			 
		}else if (command.equals("7")) {
			StmtIterator si = m.listStatements();
			Statement s = null;
			while (si.hasNext()) {
				s = si.next();
				System.out.println(s);
			}
		} else if (command.equals("8")) {
			System.out.println(m.size());
			
		}
	}
	
	private static JSONObject getPreferenceValues(JSONObject outerValues, Object innerValues, String solutionID) throws JSONException {
    	
		if(innerValues instanceof JSONObject){
    		JSONObject inValues = new JSONObject(innerValues.toString());
	        Iterator<?> inKeys = inValues.keys();
	        while( inKeys.hasNext() ){
	            String inKey = (String)inKeys.next();
	            // value  as inner object 
	            if (inValues.get(inKey) instanceof JSONObject){
	            	JSONObject newInnerValues = new JSONObject();
	            	newInnerValues = getPreferenceValues(newInnerValues, inValues.get(inKey), solutionID);
	            	outerValues.put(UPREFS.value.toString(), newInnerValues);
	            }else {
	            	// value flat
	            	if(inKey.equals("value")) {
	            		outerValues.put(UPREFS.value.toString(), inValues.get(inKey));
	            	}
	            	else outerValues.put(solutionID+"/"+inKey, inValues.get(inKey));
	            	
	            }
	        }	        	        		
	    }
		return outerValues;
	}

	static String readFile(String path, Charset encoding) throws IOException 
			{
			  byte[] encoded = Files.readAllBytes(Paths.get(path));
			  return new String(encoded, encoding);
	}
	
	static void writeFile(String path, byte[] dataToWrite)
	{
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(path);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			out.write(dataToWrite);
			out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	

}

