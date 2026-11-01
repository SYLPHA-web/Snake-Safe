package com.snakesafe.identify;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SnakePhotoRepository extends JpaRepository<SnakePhoto, Long> {

    List<SnakePhoto> findAllByOrderByCreatedAtDesc();
}
