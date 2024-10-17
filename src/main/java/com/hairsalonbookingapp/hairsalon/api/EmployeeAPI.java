package com.hairsalonbookingapp.hairsalon.api;

import com.hairsalonbookingapp.hairsalon.model.response.EmployeeInfo;
import com.hairsalonbookingapp.hairsalon.model.request.FindEmployeeRequest;
import com.hairsalonbookingapp.hairsalon.model.response.StylistInfo;
import com.hairsalonbookingapp.hairsalon.service.EmployeeService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "api")
public class EmployeeAPI {

    @Autowired
    EmployeeService employeeService;

    @PostMapping("/employee")
    public ResponseEntity getEmployeeByRole(@Valid @RequestBody FindEmployeeRequest findEmployeeRequest){
        List<EmployeeInfo> employeeInfoList = employeeService.getEmployeeByRole(findEmployeeRequest);
        return ResponseEntity.ok(employeeInfoList);
    }

    @GetMapping("/stylist")
    public ResponseEntity getAllStylist(){
        List<StylistInfo> stylistInfos = employeeService.getAllAvailableStylist();
        return ResponseEntity.ok(stylistInfos);
    }
}
