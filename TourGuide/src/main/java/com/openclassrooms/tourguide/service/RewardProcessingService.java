package com.openclassrooms.tourguide.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.openclassrooms.tourguide.user.User;

@Service
public class RewardProcessingService {

    private final RewardsService rewardsService;

    public RewardProcessingService(RewardsService rewardsService) {
        this.rewardsService = rewardsService;
    }

    public void processUsersInParallel(List<User> users) {
        List<CompletableFuture<Void>> futures = users.stream()
                .map(user -> CompletableFuture.runAsync(() -> rewardsService.calculateRewards(user)))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    public void processUsersSequentially(List<User> users) {
        for (User user : users) {
            rewardsService.calculateRewards(user);
        }
    }
}
