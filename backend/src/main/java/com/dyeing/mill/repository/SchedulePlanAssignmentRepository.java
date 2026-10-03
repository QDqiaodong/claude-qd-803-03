package com.dyeing.mill.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dyeing.mill.domain.SchedulePlanAssignment;

public interface SchedulePlanAssignmentRepository extends JpaRepository<SchedulePlanAssignment, Long> {

    List<SchedulePlanAssignment> findByPlanId(Long planId);
}
