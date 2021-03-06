package customs.controllers.peering;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.neo4j.cypher.internal.compiler.v2_2.ast.hasAggregateButIsNotAggregate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import customs.models.Churn_PoductPortfolioAndFeatures;
import customs.models.Churn_PoductPortfolioAndFeaturesDao;
import customs.models.CoreAssetDao;
import customs.models.CoreassetsAndFeaturesDao;
import customs.models.CustomsByFeatureAndCoreAssetDao;
import customs.models.Feature;
import customs.models.FeatureDao;
import customs.models.FeaturesInVariationpointsDao;
import customs.models.ParentFeature;
import customs.models.ParentFeatureDao;
import customs.models.ProductReleaseDao;
import customs.peering.FeaturePatcher;
import customs.peering.ThreeWayDiffWorkspace;
import customs.models.ProductRelease;

@Controller
public class MainProductEngController {

	@Autowired private ProductReleaseDao prDao;
	@Autowired private FeatureDao fDao;
	@Autowired private Churn_PoductPortfolioAndFeaturesDao pp_by_feature;
	@Autowired private ParentFeatureDao parentFeatureDao;
	@Autowired private CustomsByFeatureAndCoreAssetDao customsDao;
	@Autowired private CoreassetsAndFeaturesDao coreAssetsAndFeatures; 
	@Autowired private CoreAssetDao caDao; 
	private String pathToResource = "./src/main/resources/static/";
	
	@RequestMapping("/peering")
	public String getPeeringForProduct( @RequestParam(value="productbranch", required=true) String branchName,    
			@RequestParam(value="filter", required=false) String filter,@RequestParam(value="featurename", required=false) String featurename, @RequestParam(value="observed", required=false) String observed, Model model) {
		
		
		 ArrayList<String> featuresToInclude = new ArrayList<String>() ;
		 if (filter.equals("all") || filter.equals("null"))    featuresToInclude.add("all");
		 else  featuresToInclude = customs.utils.Formatting.stringToArrayList(filter, ",");
		   
		
		ArrayList<String> featuresChangedByProduct = new ArrayList<String>(); //features that the product changes
		 Iterator<Feature> it3 = fDao.findAll().iterator(); // features reused by the product - these are the ones showed in the alluvial
		 ArrayList<String> featuresReusedByProduct = new  ArrayList<String>();
		 while (it3.hasNext())
			 featuresReusedByProduct.add(it3.next().getIdfeature());
			
		
		ProductRelease p = findProductByname(branchName,"-");
		if (p ==null) return null;
		
		System.out.println("The peering product is: "+p.getName());
		
		Iterator<Churn_PoductPortfolioAndFeatures> it = pp_by_feature.findAll().iterator();		
		Churn_PoductPortfolioAndFeatures observer; // Know which features is the product customizing !!
	
		
		String pname = p.getName();
		if(p.getName().contains("-"))
			 pname=(p.getName()).split("-")[0];
		else
		  pname=p.getName();
		
		String csvCustoms="source,target,value";
		int churn = 0;
		String featuremodified ="";
		while(it.hasNext()) {//add customs for the product
			observer = it.next();
			if(observer.getId_pr() == p.getId_productrelease() && (featuresToInclude.contains(observer.getId_feature()) || featuresToInclude.contains("all") )) {
				if(! observer.getId_feature().equals("No Feature")) {
				featuresChangedByProduct.add(observer.getId_feature());
					System.out.println(observer.toString());
					featuremodified = observer.getFeaturemodified();
					churn = observer.getChurn() ;
					csvCustoms=csvCustoms.concat( "\n" +pname+ ","+featuremodified + "," +churn);
				}
			}
		}
		//add fine line to those features not customized by reusing
		Iterator<String> reusedit = featuresReusedByProduct.iterator();
		String feat;
		while (reusedit.hasNext()) {
			feat = reusedit.next();
			if( !featuresChangedByProduct.contains(feat)) //if it is reused by not customized
				csvCustoms=csvCustoms.concat("\n"+ pname + "," + feat + ",0.1" );//
		}
		
		ArrayList<String> listCustomizedFeaturesByPeers = new ArrayList<String>();
		String pr_name="";
		Iterator<Churn_PoductPortfolioAndFeatures> iterator = pp_by_feature.findAll().iterator();
		Churn_PoductPortfolioAndFeatures peers;
		while(iterator.hasNext()) {	
			peers = iterator.next();
			if( (peers.getId_pr()!=p.getId_productrelease())//if it is a peer
				&&	featuresReusedByProduct.contains(peers.getFeaturemodified()) 
				&& (featuresToInclude.contains(peers.getFeaturemodified())  || featuresToInclude.contains("all"))){
			//if(featuresChangedByProduct.contains(peers.getFeaturemodified()) && (peers.getId_pr()!=p.getId_productrelease()) && (featuresToInclude.contains(peers.getFeaturemodified())  || featuresToInclude.contains("all"))){
				listCustomizedFeaturesByPeers.add(peers.getFeaturemodified());
				featuremodified = peers.getFeaturemodified();
				churn = peers.getChurn() ;
				pr_name = peers.getPr_name();
				if (pr_name.contains("-"))
					pr_name = (pr_name.split("-"))[0];
				csvCustoms=csvCustoms.concat("\n"+ featuremodified + "," + pr_name + ","+ churn);
				}
		}		
		//for those features not in listCustomizedFeaturesByPeers add the "no_other_product" node, so that features are always placed in the middle
		Iterator<String> reused = featuresReusedByProduct.iterator();
		while(reused.hasNext()) {
			feat = reused.next();
			if(  (!listCustomizedFeaturesByPeers.contains(feat))  
				&& ((featuresToInclude.contains(feat))  || featuresToInclude.contains("all")) ) {

				csvCustoms=csvCustoms.concat("\n"+ feat + "," + "no_other_peer" + ",0.1" ); 
			}
		 }

		
		customs.utils.FileUtils.writeToFile(pathToResource+"alluvial.csv",csvCustoms);//path and test
		String filterJson = customs.utils.FeaturesToJason.getJsonForParentAndChildFeature(parentFeatureDao.findAll(), fDao); //getJsonForFeatures(fDao.findAll()); o //getJsonForParentAndChildFeature(parentFeatureDao.findAll());

		model.addAttribute("filterJson",filterJson);
		model.addAttribute("filter",filter);
		model.addAttribute("productbranch",pname);
		addFilteredFeatureToTheModel(model,featuresToInclude);
		  
		System.out.println("Before returning the HTML");
		return "alluvials/peering-kdiff";
		
	} 		
	  private ProductRelease findProductByname(String observed, String spliter) {
		  
		  Iterator<ProductRelease> ite = prDao.findAll().iterator();
		  ProductRelease  p = null;
		  while (ite.hasNext()) {
				p = ite.next();
				if ((p.getName().split(spliter)[0]).equals(observed))
					return p;
			}
		  return null;
	}
	private void addFilteredFeatureToTheModel(Model model, ArrayList<String> featuresToInclude) {
			if (featuresToInclude==null) return;

			System.out.println("seguidooo");
			String result="";
			Iterator<String> it = featuresToInclude.iterator();
			ArrayList<ParentFeature> features=new ArrayList<ParentFeature>();
			String f; ParentFeature fe;
			while(it.hasNext()) {
				f= it.next();
				fe=parentFeatureDao.getFeaturePArentByName(f);
				if (fe!=null) features.add(fe);
			}
			
			model.addAttribute("features", features);
			System.out.println(features.toString());
		}
}
