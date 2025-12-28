package com.openclassrooms.tourguide;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;

import com.openclassrooms.tourguide.model.NearbyAttractionInfo;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import tripPricer.Provider;

@RestController
public class TourGuideController {

	@Autowired
	TourGuideService tourGuideService;
    
    @Autowired
    private RewardsService rewardsService;
    
    @Autowired
    private RewardCentral rewardCentral;
    
    @Autowired
    private GpsUtil gpsUtil;
	
    @RequestMapping("/")
    public String index() {
        return "Greetings from TourGuide!";
    }
    
    @RequestMapping("/getLocation") 
    public VisitedLocation getLocation(@RequestParam String userName) {
    	return tourGuideService.getUserLocation(getUser(userName));
    }
    
    //  TODO: Change this method to no longer return a List of Attractions.
 	//  Instead: Get the closest five tourist attractions to the user - no matter how far away they are.
 	//  Return a new JSON object that contains:
    	// Name of Tourist attraction,
        // Tourist attractions lat/long,
        // The user's location lat/long, 
        // The distance in miles between the user's location and each of the attractions.
        // The reward points for visiting each Attraction.
        //    Note: Attraction reward points can be gathered from RewardsCentral
    @RequestMapping("/getNearbyAttractions")
    public List<NearbyAttractionInfo> getNearbyAttractions(@RequestParam String userName) {

        // Récupération de l'utilisateur à partir de son nom
        User user = tourGuideService.getUser(userName);

        // Récupération de la dernière position connue de l'utilisateur
        VisitedLocation visitedLocation = tourGuideService.getUserLocation(user);

        // Récupération de la liste complète de toutes les attractions disponibles
        List<Attraction> allAttractions = gpsUtil.getAttractions();
            
        // Transformation de la liste des attractions :
        // calcul de la distance
        // récupération des points de récompense
        // création d'un objet NearbyAttractionInfo
        // tri par distance
        // limitation aux 5 attractions les plus proches
        return allAttractions.stream()

            // Pour chaque attraction, on calcule les informations nécessaires
            .map(attraction -> {

                // Calcul de la distance entre l'utilisateur et l'attraction (en miles)
                double distance = rewardsService.getDistance(
                    visitedLocation.location,
                    attraction
                );

                // Récupération des points de récompense pour cette attraction
                int rewardPoints = rewardCentral.getAttractionRewardPoints(
                    attraction.attractionId,
                    user.getUserId()
                );

                // Création de l'objet de réponse contenant toutes les données de l'utilisteur
                return new NearbyAttractionInfo(
                    attraction.attractionName,                 // Nom de l'attraction
                    attraction.latitude,                       // Latitude de l'attraction
                    attraction.longitude,                      // Longitude de l'attraction
                    visitedLocation.location.latitude,         // Latitude de l'utilisateur
                    visitedLocation.location.longitude,        // Longitude de l'utilisateur
                    distance,                                  // Distance en miles
                    rewardPoints                               // Points de récompense
                );
            })

            // Tri des attractions par distance croissante (les plus proches en premier)
            .sorted((a1, a2) -> Double.compare(
                a1.getDistanceInMiles(),
                a2.getDistanceInMiles()
            ))

            // On ne garde que les 5 attractions les plus proches
            .limit(5)

            // Conversion du stream en liste
            .collect(Collectors.toList());
    }
    
    @RequestMapping("/getRewards") 
    public List<UserReward> getRewards(@RequestParam String userName) {
    	return tourGuideService.getUserRewards(getUser(userName));
    }
       
    @RequestMapping("/getTripDeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
    	return tourGuideService.getTripDeals(getUser(userName));
    }
    
    private User getUser(String userName) {
    	return tourGuideService.getUser(userName);
    }
   

}