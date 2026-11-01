package com.snakesafe.hospital;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HospitalController {

    private final HospitalService service;

    public HospitalController(HospitalService service) {
        this.service = service;
    }

    @GetMapping("/api/hospitals")
    public List<HospitalView> list(@RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return service.list(lat, lng);
    }

    @GetMapping("/api/hospitals/{id}")
    public HospitalView get(@PathVariable long id, @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return service.get(id, lat, lng);
    }

    @GetMapping("/api/emergency/best-hospital")
    public BestHospitalResponse best(@RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return service.best(lat, lng);
    }
}
