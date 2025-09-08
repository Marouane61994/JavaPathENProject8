package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.NearbyAttractionDTO;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
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

    // Pool de threads dédié pour gpsUtil (optimisation performance)
    private final ExecutorService gpsExecutor = Executors.newFixedThreadPool(
            Math.max(32, Runtime.getRuntime().availableProcessors() * 4)
    );

    // Cache pour éviter d’appeler gpsUtil inutilement
    private final ConcurrentMap<UUID, VisitedLocation> locationCache = new ConcurrentHashMap<>();

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

    /** ------------------- USERS ------------------- **/

    private final Map<String, User> internalUserMap = new HashMap<>();
    private static final String tripPricerApiKey = "test-server-api-key";

    public User getUser(String userName) {
        return internalUserMap.get(userName);
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(internalUserMap.values());
    }

    public void addUser(User user) {
        internalUserMap.putIfAbsent(user.getUserName(), user);
    }

    /** ------------------- REWARDS ------------------- **/

    public List<UserReward> getUserRewards(User user) {
        return user.getUserRewards();
    }

    /** ------------------- LOCATIONS ------------------- **/

    public VisitedLocation getUserLocation(User user) {
        return (user.getVisitedLocations().size() > 0)
                ? user.getLastVisitedLocation()
                : trackUserLocation(user).join();
    }

    public CompletableFuture<VisitedLocation> trackUserLocation(User user) {
        return CompletableFuture.supplyAsync(() -> {
            // Cache pour éviter les appels multiples inutiles
            return locationCache.computeIfAbsent(user.getUserId(), id -> {
                VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
                user.addToVisitedLocations(visitedLocation);

                // Calcul des récompenses en parallèle (sans bloquer)
                rewardsService.calculateRewards(user);

                return visitedLocation;
            });
        }, gpsExecutor);
    }

    /**
     * Récupérer en parallèle la localisation de tous les utilisateurs
     */
    public CompletableFuture<Void> trackAllUsers(List<User> users) {
        List<CompletableFuture<VisitedLocation>> futures = users.stream()
                .map(this::trackUserLocation)
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /** ------------------- TRIP DEALS ------------------- **/

    public List<Provider> getTripDeals(User user) {
        int cumulativeRewardPoints = user.getUserRewards().stream()
                .mapToInt(UserReward::getRewardPoints)
                .sum();

        List<Provider> providers = tripPricer.getPrice(
                tripPricerApiKey,
                user.getUserId(),
                user.getUserPreferences().getNumberOfAdults(),
                user.getUserPreferences().getNumberOfChildren(),
                user.getUserPreferences().getTripDuration(),
                cumulativeRewardPoints
        );
        user.setTripDeals(providers);
        return providers;
    }

    /** ------------------- ATTRACTIONS ------------------- **/

    public List<NearbyAttractionDTO> getFiveClosestAttractions(User user) {
        VisitedLocation visitedLocation = getUserLocation(user);
        Location userLocation = visitedLocation.location;

        return gpsUtil.getAttractions().stream()
                .map(attraction -> {
                    Location attractionLocation = new Location(attraction.latitude, attraction.longitude);
                    double distance = rewardsService.getDistance(attractionLocation, userLocation);

                    int rewardPoints = rewardsService.getRewardPoints(attraction, user);

                    return new NearbyAttractionDTO(
                            attraction.attractionName,
                            attraction.latitude,
                            attraction.longitude,
                            userLocation.latitude,
                            userLocation.longitude,
                            distance,
                            rewardPoints
                    );
                })
                .sorted(Comparator.comparingDouble(NearbyAttractionDTO::getDistanceMiles))
                .limit(5)
                .collect(Collectors.toList());
    }

    /** ------------------- INTERNAL TEST DATA ------------------- **/

    private void initializeInternalUsers() {
        IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
            String userName = "internalUser" + i;
            User user = new User(UUID.randomUUID(), userName, "000", userName + "@tourGuide.com");
            generateUserLocationHistory(user);
            internalUserMap.put(userName, user);
        });
        logger.debug("Created {} internal test users.", InternalTestHelper.getInternalUserNumber());
    }

    private void generateUserLocationHistory(User user) {
        IntStream.range(0, 3).forEach(i -> {
            user.addToVisitedLocations(new VisitedLocation(
                    user.getUserId(),
                    new Location(generateRandomLatitude(), generateRandomLongitude()),
                    getRandomTime()
            ));
        });
    }

    private double generateRandomLongitude() {
        return -180 + new Random().nextDouble() * 360.0;
    }

    private double generateRandomLatitude() {
        return -85.05112878 + new Random().nextDouble() * (85.05112878 * 2);
    }

    private Date getRandomTime() {
        LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
        return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
    }

    private void addShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(tracker::stopTracking));
    }
}
