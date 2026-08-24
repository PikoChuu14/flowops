package com.company.kanban.controller;
import com.company.kanban.dto.*; import com.company.kanban.entity.User; import com.company.kanban.service.RawMaterialArrivalService; import jakarta.validation.Valid; import org.springframework.http.HttpStatus; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*; import java.time.LocalDate; import java.util.List;
@RestController @RequestMapping("/api/ppc/raw-material-arrivals") public class RawMaterialArrivalController {
 private final RawMaterialArrivalService service; public RawMaterialArrivalController(RawMaterialArrivalService service){this.service=service;}
 @GetMapping public List<RawMaterialArrivalResponse> get(@RequestParam int year,@RequestParam int month,@AuthenticationPrincipal User user){return service.getItems(year,month,user);}
 @GetMapping("/{id}") public RawMaterialArrivalResponse getOne(@PathVariable Long id,@AuthenticationPrincipal User user){return service.getOne(id,user);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public RawMaterialArrivalResponse create(@Valid @RequestBody RawMaterialArrivalRequest r,@AuthenticationPrincipal User user){return service.create(r,user);}
 @PutMapping("/{id}") public RawMaterialArrivalResponse update(@PathVariable Long id,@Valid @RequestBody RawMaterialArrivalRequest r,@AuthenticationPrincipal User user){return service.update(id,r,user);}
 @PostMapping("/{id}/arrived") public RawMaterialArrivalResponse arrived(@PathVariable Long id,@RequestBody java.util.Map<String,LocalDate> body,@AuthenticationPrincipal User user){return service.markArrived(id,body.get("actualArrivalDate"),user);}
 @PostMapping("/{id}/follow-up") public RawMaterialArrivalResponse followUp(@PathVariable Long id,@Valid @RequestBody RawMaterialFollowUpRequest request,@AuthenticationPrincipal User user){return service.followUp(id,request,user);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id,@AuthenticationPrincipal User user){service.delete(id,user);}
}
