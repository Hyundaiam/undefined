package io.hyundaiam.gyeot.core

/** Explicit synthetic sensor input. No scenario IDs or verdicts are put into SignalFrame. */
object ScenarioFixtures {
    val ids = (1..25).flatMap { listOf("G%02d".format(it), "B%02d".format(it)) }
    fun frames(id: String): List<SignalFrame> {
        require(id in ids) { "Unknown fixture: $id" }
        val baseline = SignalFrame(0, SignalSource.DEMO)
        fun steady(seconds: Int, signal: SignalFrame) = (0..seconds).map { signal.copy(timeMs = it * 1_000L) }
        return when(id) {
            "G01" -> steady(21, baseline.copy(speedKph=50.0, distanceM=45.0))
            "B01" -> steady(4, baseline.copy(speedKph=50.0, distanceM=10.0))
            "G02" -> steady(31, baseline.copy(speedKph=55.0, speedLimitKph=60.0))
            "B02" -> steady(3, baseline.copy(speedKph=74.0, speedLimitKph=60.0))
            "G03" -> (0..5).map { baseline.copy(timeMs=it*1_000L, speedKph=it*3.0) }
            "B03" -> (0..3).map { baseline.copy(timeMs=it*1_000L, speedKph=it*15.0) }
            "G04" -> listOf(20.0,17.0,14.0,11.0,8.0,5.0,2.0,0.0).mapIndexed { i,s -> baseline.copy(timeMs=i*1_000L,speedKph=s) }
            "B04" -> listOf(baseline.copy(speedKph=70.0,emergencyBraking=false),baseline.copy(timeMs=1_000,speedKph=20.0,emergencyBraking=false))
            "G05" -> steady(6,baseline.copy(speedKph=25.0,recommendedCurveSpeed=30.0))
            "B05" -> steady(3,baseline.copy(speedKph=55.0,recommendedCurveSpeed=30.0))
            "G06" -> (0..3).map { baseline.copy(timeMs=it*1_000L,speedKph=40.0,indicatorOn=true,laneChange=it>=2) }
            "B06" -> steady(1,baseline.copy(speedKph=40.0,indicatorOn=false,laneChange=true))
            "G07" -> steady(11,baseline.copy(speedKph=45.0,laneDeviation=false))
            "B07" -> steady(4,baseline.copy(speedKph=45.0,laneDeviation=true))
            "G08" -> steady(2,baseline.copy(speedKph=0.0,pedestrianAhead=true,crosswalkAhead=true,pedestrianYielded=true))
            "B08" -> steady(2,baseline.copy(speedKph=40.0,pedestrianAhead=true,crosswalkAhead=true,pedestrianYielded=false))
            "G09" -> steady(6,baseline.copy(speedKph=30.0,distanceM=35.0,wetRoad=true,weatherRecommendedSpeed=35.0))
            "B09" -> steady(3,baseline.copy(speedKph=55.0,distanceM=15.0,wetRoad=true,weatherRecommendedSpeed=35.0))
            "G10" -> steady(11,baseline.copy(speedKph=50.0,tunnel=true,windowOpen=false))
            "B10" -> steady(3,baseline.copy(speedKph=50.0,tunnel=true,windowOpen=true))
            "G11" -> steady(31,baseline.copy(speedKph=40.0,handsOnWheel=2))
            "B11" -> steady(9,baseline.copy(speedKph=40.0,handsOnWheel=1))
            "G12" -> steady(11,baseline.copy(speedKph=40.0,automaticTransmission=true,bothFeetOnPedals=false))
            "B12" -> steady(4,baseline.copy(speedKph=40.0,automaticTransmission=true,bothFeetOnPedals=true))
            "G13" -> steady(31,baseline.copy(speedKph=40.0,seatDeviationFraction=.05))
            "B13" -> steady(11,baseline.copy(speedKph=40.0,seatDeviationFraction=.4))
            "G14" -> steady(31,baseline.copy(speedKph=40.0,occupiedSeats=setOf("driver","passenger"),beltBySeat=mapOf("driver" to true,"passenger" to true)))
            "B14" -> steady(3,baseline.copy(speedKph=40.0,occupiedSeats=setOf("driver","passenger"),beltBySeat=mapOf("driver" to true,"passenger" to false)))
            "G15" -> steady(2,baseline.copy(speedKph=0.0,gear="GEAR_PARK",ignitionOn=false,parkingBrakeOn=true))
            "B15" -> steady(2,baseline.copy(speedKph=0.0,gear="GEAR_PARK",ignitionOn=true,parkingBrakeOn=false,driverLeaving=true))
            "G16" -> steady(6,baseline.copy(speedKph=25.0,speedLimitKph=30.0,schoolZone=true))
            "B16" -> steady(3,baseline.copy(speedKph=45.0,speedLimitKph=30.0,schoolZone=true))
            "G17" -> steady(2,baseline.copy(speedKph=0.0,redLight=true,stopLineDistanceM=2.0))
            "B17" -> steady(1,baseline.copy(speedKph=15.0,redLight=true,stopLineDistanceM=-1.0))
            "G18" -> steady(9,baseline.copy(speedKph=50.0,distanceM=55.0,night=true))
            "B18" -> steady(3,baseline.copy(speedKph=50.0,distanceM=20.0,night=true))
            "G19" -> steady(2,baseline.copy(speedKph=0.0,emergencyVehicle=true,emergencyYielded=true))
            "B19" -> steady(4,baseline.copy(speedKph=40.0,emergencyVehicle=true,emergencyYielded=false))
            "G20" -> steady(3,baseline.copy(speedKph=40.0,blindSpotOccupied=true,indicatorOn=true,laneChange=false))
            "B20" -> steady(1,baseline.copy(speedKph=40.0,blindSpotOccupied=true,indicatorOn=true,laneChange=true))
            "G21" -> steady(2,baseline.copy(speedKph=0.0,gear="GEAR_PARK",drowsy=true,restStop=true))
            "B21" -> steady(3,baseline.copy(speedKph=40.0,drowsy=true))
            "G22" -> steady(6,baseline.copy(speedKph=25.0,doorsOpen=false))
            "B22" -> steady(1,baseline.copy(speedKph=25.0,doorsOpen=true))
            "G23" -> steady(2,baseline.copy(speedKph=0.0,gear="GEAR_PARK",tirePressureLow=false))
            "B23" -> steady(3,baseline.copy(speedKph=40.0,tirePressureLow=true))
            "G24" -> steady(6,baseline.copy(speedKph=40.0,remainingRangeKm=150.0,routeRemainingKm=70.0))
            "B24" -> steady(3,baseline.copy(speedKph=40.0,remainingRangeKm=30.0,routeRemainingKm=70.0))
            "G25" -> steady(2,baseline.copy(speedKph=40.0,distanceM=35.0,mergeZone=true,laneChange=true,indicatorOn=true))
            "B25" -> steady(2,baseline.copy(speedKph=40.0,distanceM=10.0,mergeZone=true,laneChange=true,indicatorOn=true))
            else -> error("Unreachable")
        }
    }
}
