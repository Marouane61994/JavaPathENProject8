package com.openclassrooms.tourguide.service;

import java.util.*;
import java.util.concurrent.*;
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
    private final List<Attraction> attractionsCache;

    // Cache des points de récompense : (attractionId-userId) → points
    private final ConcurrentMap<String, Integer> rewardCache = new ConcurrentHashMap<>();

    // Pool de threads dédié pour gérer les requêtes RewardCentral
    private final ExecutorService rewardsExecutor = Executors.newFixedThreadPool(
            Math.max(16, Runtime.getRuntime().availableProcessors() * 2)
    );

    public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
        this.gpsUtil = gpsUtil;
        this.rewardsCentral = rewardCentral;
        this.attractionsCache = gpsUtil.getAttractions();
    }

    public void setProximityBuffer(int proximityBuffer) {
        this.proximityBuffer = proximityBuffer;
    }

    public void setDefaultProximityBuffer() {
        proximityBuffer = defaultProximityBuffer;
    }

    /**
     * Calcul asynchrone des récompenses d’un utilisateur
     */
    public CompletableFuture<Void> calculateRewards(User user) {
        List<VisitedLocation> userLocations = new ArrayList<>(user.getVisitedLocations());

        // Pré-filtrer uniquement les attractions proches pour éviter de tout comparer
        List<Attraction> nearbyAttractions = attractionsCache.stream()
                .filter(attraction -> userLocations.stream()
                        .anyMatch(visited -> nearAttraction(visited, attraction)))
                .collect(Collectors.toList());

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (VisitedLocation visitedLocation : userLocations) {
            for (Attraction attraction : nearbyAttractions) {
                boolean alreadyRewarded = user.getUserRewards().stream()
                        .anyMatch(r -> r.attraction.attractionName.equals(attraction.attractionName));

                if (!alreadyRewarded) {
                    CompletableFuture<Void> future = getRewardPointsAsync(attraction, user)
                            .thenAccept(rewardPoints -> {
                                if (rewardPoints > 0) {
                                    user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
                                }
                            });
                    futures.add(future);
                }
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Récupération asynchrone des points avec cache
     */
    private CompletableFuture<Integer> getRewardPointsAsync(Attraction attraction, User user) {
        return CompletableFuture.supplyAsync(() -> {
            String key = attraction.attractionId.toString() + "-" + user.getUserId();
            return rewardCache.computeIfAbsent(key,
                    k -> rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId()));
        }, rewardsExecutor);
    }

    public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
        return getDistance(attraction, location) <= attractionProximityRange;
    }

    private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
        return getDistance(attraction, visitedLocation.location) <= proximityBuffer;
    }

    public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        return STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
    }
    public int getRewardPoints(Attraction attraction, User user) {
        String key = attraction.attractionId.toString() + "-" + user.getUserId();
        return rewardCache.computeIfAbsent(key,
                k -> rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId()));
    }

}
