package gpii;

import gpii.schemas.UPREFS;
import gpii.contexts.*;

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
		System.out.println("[4] SPARQL Query: detect conflicts");
		
		System.out.println("[5] Resolve confilcts");
		System.out.println("[6] Output: Results as JSONLD object");

		// Helper functions 
		System.out.println("[7] Print all statements of model m");
		System.out.println("[8] Print number of statements of model m");
		
		System.out.println("[Q] Quit");

		input = br.readLine();

		return input;
	}

	public static void execute(String command) throws IOException, JSONException, URISyntaxException {
		if (command.equals("0")){
			LOG.info("Pre-processing - add context to preferences and device characteristics and store as JSONLD");
			//TODO add context in pre-processing in Javascript part
			/** 
			 * pre-processing of preferences  
			 * input: JSON object - same as always (see test file t2Common.json)
			 * pre-processing: (1) load context (see test file contexts/preferenesContext.jsonld); 
			 * (2) transform device characteristics from JSONObject to JSONArray and (3) put JSONArray to context
			 * output: JSON-LD stored locally (see test file t2Device.jsonld)
			 */
			JSONObject preferences; 
			JSONObject pContext;			
			
			// (1) load context
			String pContextFile = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\contexts\\preferencesContext.jsonld";
			String pContextString = readFile(pContextFile, StandardCharsets.UTF_8);			
			JSONTokener pContextTokener = new JSONTokener(pContextString);
			pContext = new JSONObject(pContextTokener);
			
			// (2) transform device characteristics from JSONObject to JSONArray
			// TODO: input a JSON Object not a file 
			String preferenceFile = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\t2Common.json";
			String preferenceString = readFile(preferenceFile, StandardCharsets.UTF_8);
			JSONTokener preferencesTokener = new JSONTokener(preferenceString);
			preferences = new JSONObject(preferencesTokener);			
		
			// (3) put JSONArray to context
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
	            JSONObject innerPrefsObject = new JSONObject (); 
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
		    pContext.put(UPREFS.Preference.toString(), prefsArray);
		    // output: store as file: t2Common.jsonld
		    byte dataToWrite[] = pContext.toString().getBytes(StandardCharsets.US_ASCII);
		    // TODO: organize project directories; 
		    writeFile("C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\t2Common.jsonld", dataToWrite);
		    
			/** 
			 * pre-processing of device characteristics  
			 * input: JSON object - same as always (see test file t2Device.json)
			 * pre-processing: (1) load context (see test file contexts/deviceContext.jsonld); 
			 * (2) transform device characteristics from JSONObject to JSONArray and (3) put JSONArray to context
			 * output: JSON-LD stored locally (see test file t2Device.jsonld)
			 */
			JSONArray device; 
			JSONObject dContext;			
			
			// (1) load context
			String contextFile = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\contexts\\deviceContext.jsonld";
			String contextString = readFile(contextFile, StandardCharsets.UTF_8);			
			JSONTokener contextTokener = new JSONTokener(contextString);
			dContext = new JSONObject(contextTokener);
			
			// (2) transform device characteristics from JSONObject to JSONArray
			// TODO: input a JSON Object not a file 
			String deviceFile = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\t2Device.json";
			String deviceString = readFile(deviceFile, StandardCharsets.UTF_8);
			JSONTokener deviceTokener = new JSONTokener(deviceString);
			device = new JSONArray(deviceTokener);			
		
			// (3) put JSONArray to context
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
		    dContext.put("solutions", sol);		    
		    // output: store as file: t2Device.jsonld
		    byte cDataToWrite[] = dContext.toString().getBytes(StandardCharsets.US_ASCII);
		    // TODO: organize project directories; 
		    writeFile("C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\t2Device.jsonld", cDataToWrite);
		    
		}
		else if (command.equals("1")) {
			
			LOG.info("Load preferences (JSONLD) and device characteristics (JSONLD)");

			// both input shall be either fetched from GPII or arguments in a match request
			String prefs = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\t2Common.jsonld";
			// automatically created in [0]
			String device = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\t2Device.jsonld";
			
			// load accessibility namespace
			m.setNsPrefix("ax", UPREFS.NS);
			m = ModelFactory.createDefaultModel().read(prefs, "JSON-LD");
			Model d = ModelFactory.createDefaultModel().read(device, "JSON-LD");
			m.add(d);
			m.write(System.out);

			// TODO: use ModelFactors or RDFDataMrg ? 
			//alternative to read preferences from JSONLD			
			//RDFDataMgr.read(m, inputURL.toUri().toString(), null, JenaJSONLD.JSONLD);
			 
		}
		else if (command.equals("2")) {
			LOG.info("Load other semantic data source (registry and solutions)");
			/** 
			 * TODO this are static input source used in the RBMM Web Service.
			 * These input sources should be exchangeable with any other semantic representations of preference terms or solutions
			 */
		    //String reg = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\registry.jsonld";
		    String sol = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\solutions.jsonld";

		    //Model registry = ModelFactory.createDefaultModel().read(reg, "JSON-LD");
	        Model uListing = ModelFactory.createDefaultModel().read(sol, "JSON-LD");
	        
	        // merge the Models
	        //m = m.union(registry);
	        m = m.union(uListing);
	        
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
		    String mappingRules = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\mappingRules.rules";

		    File f = new File(mappingRules);
			if (f.exists()) {
				List<Rule> rules = Rule.rulesFromURL("file:" + mappingRules);
				GenericRuleReasoner r = new GenericRuleReasoner(rules);
				InfModel infModel = ModelFactory.createInfModel(r, m);
			      // starting the rule execution
			     infModel.prepare();					
			      // write down the results in RDF/XML form
			     infModel.write(System.out);			
			    
			    // TODO why am I doing this here?  
				m.add(infModel.getDeductionsModel());
			} else
				System.out.println("That rules file does not exist.");
		} 
		else if (command.equals("4")){
			LOG.info("SPARQL Query: detect conflicts");
			  /**
			   * TODO 
			   * Fix: ?y is not constructed in the RDF model.
			   * SPARQL query not in source code 
			   */

			/*String constructString = "CONSTRUCT";
			  constructString += "{ ";
			  constructString += " <http://gpii.org/schemas/accessibility#Environment> <http://gpii.org/schemas/accessibility#accessibilityConflict> <http://gpii.org/schemas/accessibility#MultipleSolutionsConflict> .";
			  constructString += " <http://gpii.org/schemas/accessibility#MultipleSolutionsConflict> <http://gpii.org/schemas/accessibility#applyATType> ?x .";
			  constructString += " <http://gpii.org/schemas/accessibility#MultipleSolutionsConflict> <http://gpii.org/schemas/accessibility#applyATProduct> ?y .";
			  constructString += " } ";
			  constructString += "WHERE { ";
			  constructString += " SELECT ?x (COUNT(?y) AS ?count) ";
				  constructString += "{ ";
				  constructString += "<http://gpii.org/schemas/accessibility#User> <http://gpii.org/schemas/accessibility#requiresAT> ?x . ";
				  constructString += "?y <http://registry.gpii.org/applications/type> ?x . ";	
				  constructString += "} GROUP BY ?x";
				  constructString += " HAVING (?count > 1)";
				  constructString += " }";*/
		    String queryStringFile = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\detectMultipleSolutionConflict.sparql";
		    String queryString = readFile(queryStringFile, StandardCharsets.UTF_8); 
			  
			  
			  Query query = QueryFactory.create(queryString) ;
			  QueryExecution qexec = QueryExecutionFactory.create(query, m) ;
			  acm = qexec.execConstruct() ;
			  m.add(acm);
			  m.write(System.out);
			  // out-dated print all constructed RDF tripled  
			  /*StmtIterator si = acm.listStatements();
			  Statement s = null;
				while (si.hasNext()) {
					s = si.next();
					System.out.println(s);
				}*/
			  qexec.close();		
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
			m.add(acm);
			m.write(System.out);
		    
		    /*QueryExecution qexec = QueryExecutionFactory.create(query, m) ;
			  try {
				    ResultSet results = qexec.execSelect() ;
				    ResultSetFormatter.out(System.out, results, query) ;
				  } finally { qexec.close() ; }*/			
			///acm = qexec.execConstruct() ;
			//m.add(acm);
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

