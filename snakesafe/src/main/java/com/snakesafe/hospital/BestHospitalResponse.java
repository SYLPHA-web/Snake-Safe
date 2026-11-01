package com.snakesafe.hospital;

import java.util.List;

public record BestHospitalResponse(boolean found, HospitalView hospital, List<HospitalView> others, String message) {
}
