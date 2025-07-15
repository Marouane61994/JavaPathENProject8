# Technologies

> Java 17  
> Spring Boot 3.X  
> JUnit 5  

# How to have gpsUtil, rewardCentral and tripPricer dependencies available ?

> Run : 
- mvn install:install-file -Dfile=C:\Users\Win10\Desktop\Projet8\JavaPathENProject8\TourGuide\libs\gpsUtil.jar -DgroupId=gpsUtil -DartifactId=gpsUtil -Dversion=1.0.0 -Dpackaging=jar  
- mvn install:install-file -Dfile=C:\Users\Win10\Desktop\Projet8\JavaPathENProject8\TourGuide\libs\RewardCentral.jar -DgroupId=rewardCentral -DartifactId=rewardCentral -Dversion=1.0.0 -Dpackaging=jar  
- mvn install:install-file -Dfile=C:\Users\Win10\Desktop\Projet8\JavaPathENProject8\TourGuide\libs\TripPricer.jar -DgroupId=tripPricer -DartifactId=tripPricer -Dversion=1.0.0 -Dpackaging=jar
