package com.company.kanban.repository;
import com.company.kanban.entity.RawMaterialArrival;
import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
import java.time.LocalDate; import java.util.List;
public interface RawMaterialArrivalRepository extends JpaRepository<RawMaterialArrival,Long> {
    @Query("select item from RawMaterialArrival item where item.expectedArrivalDate between :start and :end order by item.expectedArrivalDate, item.id")
    List<RawMaterialArrival> findForMonth(@Param("start") LocalDate start,@Param("end") LocalDate end);
    List<RawMaterialArrival> findByActualArrivalDateIsNull();
}
