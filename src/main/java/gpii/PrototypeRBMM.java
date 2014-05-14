package gpii;

import gpii.schemas.UPREFS;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.lang.PipedRDFIterator;

import com.github.jsonldjava.utils.*;
import com.github.jsonldjava.jena.*;
import com.github.jsonldjava.core.*;
import com.github.jsonldjava.impl.*;


import com.hp.hpl.jena.rdf.model.InfModel;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.reasoner.rulesys.GenericRuleReasoner;
import com.hp.hpl.jena.reasoner.rulesys.Rule;
import com.hp.hpl.jena.shared.DoesNotExistException;
import com.hp.hpl.jena.query.*;

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
				execute(in);				
			} catch (Exception e) {
				in = "";
			}
		}
	}

	public static String getUserInput() throws IOException {
		br = new BufferedReader(new InputStreamReader(System.in));
		String input = null;

		System.out.println("Please enter a command");
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

	public static void execute(String command) throws IOException {
		if (command.equals("1")) {
			
			LOG.info("Load preferences (JSONLD) and device characteristics (JSONLD)");

			// both input shall be either fetched from GPII or arguments in a match request
			String prefs = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\t1Common.jsonld";
			String device = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\t1Device.jsonld";
			
			// load accessibility namespace
			m.setNsPrefix("ax", UPREFS.NS);
			m = ModelFactory.createDefaultModel().read(prefs, "JSON-LD");
		    Model solutions = ModelFactory.createDefaultModel().read(device, "JSON-LD");
	        m = m.union(solutions);
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
		    String reg = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\registry.jsonld";
		    String sol = "C:\\eclipse\\workspace\\PrototypeRBMM_Maven\\RBMMPlayground\\src\\main\\java\\gpii\\solutions.jsonld";

		    Model registry = ModelFactory.createDefaultModel().read(reg, "JSON-LD");
	        Model uListing = ModelFactory.createDefaultModel().read(sol, "JSON-LD");
	        
	        // merge the Models
	        m = m.union(registry);
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

			String constructString = "CONSTRUCT";
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
				  constructString += " }";
			  
			  
			  Query query = QueryFactory.create(constructString) ;
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
			LOG.info("Resolve confilcts => not implemented");
			/**
			 * TODO implement conflict resolution
			 */
			System.out.println("still not implemented"); 
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
}

