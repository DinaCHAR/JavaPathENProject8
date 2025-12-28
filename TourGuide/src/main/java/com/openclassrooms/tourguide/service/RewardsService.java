package com.openclassrooms.tourguide.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

@Service
public class RewardsService {

    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	
	private final List<Attraction> cachedAttractions = new ArrayList<>();

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
		// Cache attractions at initialization
		cachedAttractions.addAll(gpsUtil.getAttractions());
	}
	
	public RewardCentral getRewardCentral() {
		return rewardsCentral;
	}
	
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}
	
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

		// Création d'un pool de threads
		// Le nombre de threads est basé sur le nombre de cœurs CPU disponibles multiplié par 4
		// Cela permet d'exécuter plusieurs tâches en parallèle
		private final ExecutorService executorService =
		    Executors.newFixedThreadPool(
		        Runtime.getRuntime().availableProcessors() * 4
		    );
		
	public void calculateRewards(User user) {
		// Récupération de l'historique des lieux visités par l'utilisateur
		List<VisitedLocation> userLocations = user.getVisitedLocations();
		
		// Si l'utilisateur n'a visité aucun lieu, on arrête le traitement
		if (userLocations.isEmpty()) {
			return;
		}

		// Création d'un ensemble thread-safe pour stocker les noms des attractions déjà récompensées
		Set<String> existingRewards = ConcurrentHashMap.newKeySet();
		existingRewards.addAll(user.getUserRewards().stream()
			.map(r -> r.attraction.attractionName)
			.collect(Collectors.toList()));

		// Création des tâches asynchrones pour chaque lieu visité
		List<CompletableFuture<List<UserReward>>> futures = userLocations.stream()
			.map(visitedLocation -> CompletableFuture.supplyAsync(() -> 
				// Pour chaque lieu visité, on filtre les attractions :
				cachedAttractions.stream()
					// On exclut les attractions déjà récompensées
					.filter(attraction -> !existingRewards.contains(attraction.attractionName))
					// On vérifie si l'utilisateur était proche de l'attraction
					.filter(attraction -> nearAttraction(visitedLocation, attraction))
					// On ajoute l'attraction à l'ensemble des récompenses existantes
					.filter(attraction -> existingRewards.add(attraction.attractionName))
					// On crée une nouvelle récompense pour chaque attraction éligible
					.map(attraction -> new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)))
					.collect(Collectors.toList()), executorService))
			.collect(Collectors.toList());

		// Attente de la fin de toutes les tâches et ajout des récompenses à l'utilisateur
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
			.thenAccept(v -> futures.stream()
				.map(CompletableFuture::join)
				.flatMap(List::stream)
				.forEach(user::addUserReward));

		// Attente de la fin de toutes les tâches
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
	}



	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}
	
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}
	
	private int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}
	
	public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
	}
}