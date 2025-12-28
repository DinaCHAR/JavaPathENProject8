package com.openclassrooms.tourguide.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.model.NearbyAttractionInfo;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		
		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
				: trackUserLocation(user);
		return visitedLocation;
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	// Création d'un pool de threads
	// Le nombre de threads est basé sur le nombre de cœurs CPU disponibles multiplié par 4
	// Cela permet d'exécuter plusieurs tâches en parallèle (GPS + rewards)
	private final ExecutorService executorService =
	    Executors.newFixedThreadPool(
	        Runtime.getRuntime().availableProcessors() * 4
	    );

	public VisitedLocation trackUserLocation(User user) {
	    try {

	        // Création d'une tâche asynchrone qui récupère la position de l'utilisateur
	        // CompletableFuture permet d'exécuter cette tâche dans un thread du pool
	        CompletableFuture<VisitedLocation> locationFuture =
	            CompletableFuture.supplyAsync(() -> {

	                // Appel au service GPS pour récupérer la position de l'utilisateur
	                VisitedLocation visitedLocation =
	                    gpsUtil.getUserLocation(user.getUserId());

	                // Ajout de la position récupérée à l'historique des localisations de l'utilisateur
	                user.addToVisitedLocations(visitedLocation);

	                // Retour de la position visitée
	                return visitedLocation;

	            }, executorService);

	        // Récupération du résultat de la tâche asynchrone
	        // Cette ligne est bloquante : le thread attend que la position soit disponible
	        VisitedLocation visitedLocation = locationFuture.get();

	        // Lancement asynchrone du calcul des récompenses
	        // Cette tâche ne bloque pas le retour de la méthode
	        CompletableFuture.runAsync(
	            () -> rewardsService.calculateRewards(user),
	            executorService
	        );

	        // Retour de la position visitée à l'appelant
	        return visitedLocation;

	    } catch (Exception e) {

	        // Gestion des exceptions :
	        // toute erreur survenue lors de l'exécution asynchrone
	        // est encapsulée dans une RuntimeException
	        throw new RuntimeException("Error tracking user location", e);
	    }
	}

	//ANCIENNE METHODE
	/*public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
	    // Récupération de toutes les attractions disponibles via le service GPS
	    return gpsUtil.getAttractions().stream()
	        // Tri des attractions par distance croissante par rapport à la position de l'utilisateur
	        // Les attractions les plus proches seront placées en premier
	        .sorted((a1, a2) -> Double.compare(
	            rewardsService.getDistance(visitedLocation.location, a1),
	            rewardsService.getDistance(visitedLocation.location, a2)
	        ))

	        // Limitation de la liste aux 5 attractions les plus proches
	        .limit(5)

	        // Conversion du stream en liste de résultats
	        .collect(Collectors.toList());
	}*/
	
	//METHODE CORRIGER POUR RENVOYER LES CINQ ATTRACTIONS
	public List<NearbyAttractionInfo> getNearByAttractions(VisitedLocation visitedLocation){
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
                int rewardPoints = rewardsService.getRewardCentral().getAttractionRewardPoints(
                    attraction.attractionId,
                    visitedLocation.userId
                );
                
                Location locationAttraction = new Location(attraction.latitude, attraction.longitude);

                Location locationUser = visitedLocation.location;
                
                // Création de l'objet de réponse contenant toutes les données de l'utilisteur
                return new NearbyAttractionInfo(
                    attraction.attractionName,
                    locationAttraction,							//Latitude et Longitude (encapsule les deux)
                    locationUser,								//Latitude et longitude 
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

	private void addShutDownHook() {

	    // Ajout d'un hook d'arrêt de la JVM
	    // Ce hook est exécuté automatiquement lorsque l'application se termine
	    Runtime.getRuntime().addShutdownHook(new Thread() {

	        // Code exécuté lors de l'arrêt de l'application
	        public void run() {

	            // Arrêt propre du tracker afin d'éviter des threads actifs
	            tracker.stopTracking();
	        }
	    });
	}

	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}
