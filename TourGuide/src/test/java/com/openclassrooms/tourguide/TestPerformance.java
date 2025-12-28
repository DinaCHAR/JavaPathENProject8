package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

public class TestPerformance {

	/*
	 * A note on performance improvements:
	 * 
	 * The number of users generated for the high volume tests can be easily
	 * adjusted via this method:
	 * 
	 * InternalTestHelper.setInternalUserNumber(100000);
	 * 
	 * 
	 * These tests can be modified to suit new solutions, just as long as the
	 * performance metrics at the end of the tests remains consistent.
	 * 
	 * These are performance metrics that we are trying to hit:
	 * 
	 * highVolumeTrackLocation: 100,000 users within 15 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(15) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 *
	 * highVolumeGetRewards: 100,000 users within 20 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(20) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 */

	@Disabled
	@Test
	public void highVolumeTrackLocation() {
		// Création du service GPS
	    // Permet de simuler la récupération des positions des utilisateurs
		GpsUtil gpsUtil = new GpsUtil();
		// Création du service de récompenses
	    // Il sera utilisé lors du calcul des distances et des points de récompense
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		// Test avac 100000 users
		InternalTestHelper.setInternalUserNumber(100000);
		// Création du service principal TourGuideService
	    // Il gère le suivi de la localisation et les récompenses
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		// Récupération de la liste complète des utilisateurs simulés
		List<User> allUsers = tourGuideService.getAllUsers();

		// Création et démarrage d'un chronomètre
	    // Sert à mesurer le temps d'exécution du traitement
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		// Process all users in parallel
	    // Chaque utilisateur voit sa position GPS suivie simultanément
		allUsers.parallelStream().forEach(user -> tourGuideService.trackUserLocation(user));

		// Arrêt du chronomètre après la fin du traitement
		stopWatch.stop();
		// Arrêt du tracker pour éviter des threads actifs après le test
		tourGuideService.tracker.stopTracking();

		// Conversion du temps écoulé de millisecondes en secondes
		long timeElapsed = TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime());
		System.out.println("highVolumeTrackLocation: Time Elapsed: " + timeElapsed + " seconds.");
		// Conversion du temps écoulé de millisecondes en secondes
		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= timeElapsed);
	}

	@Disabled
	@Test
	public void highVolumeGetRewards() {
		//Création du service GPS
		//Utilisé pour récupérer les attractions et simuler les données de localisation
		GpsUtil gpsUtil = new GpsUtil();
		// Création du service de récompenses
		// Responsable du calcul des points liés aux attractions visitées
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		// Test avec 100000 users
		InternalTestHelper.setInternalUserNumber(100000);
		//Création du service principal TourGuideService
		// Il centralise la gestion des utilisateurs et des récompenses
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		//Sélection d'une attraction de référence
		//La première attraction de la liste est utilisée pour le test
		Attraction attraction = gpsUtil.getAttractions().get(0);
		// Récupération de la liste complète des utilisateurs simulés
		List<User> allUsers = tourGuideService.getAllUsers();

		// Initialisation et démarrage du chronomètre
		//Permet de mesurer le temps total d'exécution du test
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		// Process all users in parallel
		// Pour chaque utilisateur :
		//on simule la visite d'une attraction
		//on calcule les récompenses associées
		allUsers.parallelStream().forEach(user -> {
			// Ajout manuel d'une localisation visitée par l'utilisateur
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(), attraction, new Date()));
			 // Calcul des récompenses pour l'utilisateur
			rewardsService.calculateRewards(user);
		});

		// Verify rewards
		allUsers.parallelStream().forEach(user -> assertTrue(user.getUserRewards().size() > 0));

		//Arrêt du chronomètre après la fin de tous les traitements
		stopWatch.stop();
		// Arrêt du tracker pour éviter les threads actifs après l'exécution du test
		tourGuideService.tracker.stopTracking();

		// Conversion du temps écoulé de millisecondes en secondes
		long timeElapsed = TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime());
		System.out.println("highVolumeGetRewards: Time Elapsed: " + timeElapsed + " seconds.");
		// Vérification que le test s'exécute en moins de 20 minutes
		assertTrue(TimeUnit.MINUTES.toSeconds(20) >= timeElapsed);
	}

}