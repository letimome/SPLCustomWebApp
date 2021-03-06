package customs.models;


import javax.transaction.Transactional;
import org.springframework.data.repository.CrudRepository;

@Transactional
public interface FeaturesInVariationpointsDao extends CrudRepository<FeaturesInVariationpoints, Long>{
	
	Iterable<FeaturesInVariationpoints> getCoreAssetsByIdfeature(String idfeature);
}
