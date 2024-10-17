package com.hairsalonbookingapp.hairsalon.service;

import com.hairsalonbookingapp.hairsalon.entity.*;
import com.hairsalonbookingapp.hairsalon.exception.EntityNotFoundException;
import com.hairsalonbookingapp.hairsalon.model.EmailDetail;
import com.hairsalonbookingapp.hairsalon.model.EmailDetailDeleteAppointment;
import com.hairsalonbookingapp.hairsalon.model.request.AppointmentRequest;
import com.hairsalonbookingapp.hairsalon.model.request.AppointmentUpdate;
import com.hairsalonbookingapp.hairsalon.model.request.CompleteAppointmentRequest;
import com.hairsalonbookingapp.hairsalon.model.request.DeleteAllAppointmentsRequest;
import com.hairsalonbookingapp.hairsalon.model.response.AppointmentResponse;
import com.hairsalonbookingapp.hairsalon.model.response.AvailableSlot;
import com.hairsalonbookingapp.hairsalon.model.response.EmailDetailCreateAppointment;
import com.hairsalonbookingapp.hairsalon.model.response.KPITotal;
import com.hairsalonbookingapp.hairsalon.repository.*;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AppointmentService {

    @Autowired
    SlotService slotService;

    @Autowired
    ShiftEmployeeService shiftEmployeeService;

    @Autowired
    AppointmentRepository appointmentRepository;

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    EmployeeService employeeService;

    @Autowired
    HairSalonBookingAppService hairSalonBookingAppService;

    @Autowired
    ServiceRepository serviceRepository;

    @Autowired
    AuthenticationService authenticationService;

    @Autowired
    EmployeeRepository employeeRepository;

    @Autowired
    AccountForCustomerRepository accountForCustomerRepository;

    @Autowired
    SlotRepository slotRepository;

    @Autowired
    TimeService timeService;
    @Autowired
    EmailService emailService;

    @Autowired
    DiscountCodeRepository discountCodeRepository;

    //CUSTOMER XEM VÀ CHỌN DỊCH VỤ
    //- CHỨC NĂNG getAllAvailableService(); BÊN HAIR SALON BOOKING APP SERVICE : CUSTOMER XEM CÁC DỊCH VỤ KHẢ DỤNG

    //CUSTOMER XEM NGÀY HÔM NAY VÀ NGÀY TIẾP THEO CÓ CÁC CA LÀM VIỆC CỦA AI
    //- CHỨC NĂNG getAllAvailableSlots(); BÊN SHIFT EMPLOYEE SERVICE: CUSTOMER XEM CA LÀM VIỆC CỦA STYLIST VÀ LỰA CHỌN


    //CUSTOMER NHẬP MÃ GIẢM GIÁ (TÙY CHỌN)
    public DiscountCode getDiscountCode(String code) {    // HÀM LẤY MÃ GIẢM GIÁ
        DiscountCode discountCode = discountCodeRepository.findDiscountCodeByDiscountCodeId(code);
        if(discountCode != null && discountCode.getAppointment() == null){
            return discountCode;
        } else {
            throw new EntityNotFoundException("Code not available!");
        }
    }

    //TEST CHO VUI
    /*public long getAppoint(AppointmentRequest appointmentRequest){
        return appointmentRequest.getServiceIdList().get(0);
    }*/

    //HỆ THỐNG CHỐT -> CUSTOMER LÀM
    public AppointmentResponse createNewAppointment(AppointmentRequest appointmentRequest) {
        try {
            List<String> serviceNameList = new ArrayList<>();
            List<Long> serviceIdList = appointmentRequest.getServiceIdList();  // NGƯỜI DÙNG CHỌN NHIỀU LOẠI DỊCH VỤ
            List<HairSalonService> hairSalonServiceList = new ArrayList<>();
            double bonusDiscountCode = 0;    // PHÍ GIẢM GIÁ CỦA MÃ (NẾU CÓ)
            double bonusEmployee = 0;   // PHÍ TRẢ THÊM CHO STYLIST DỰA TRÊN CẤP ĐỘ
            double serviceFee = 0;
            for(long serviceId : serviceIdList){
                HairSalonService service = serviceRepository.findHairSalonServiceById(serviceId);
                hairSalonServiceList.add(service);
                serviceNameList.add(service.getName());
                serviceFee += service.getCost();  // PHÍ GỐC CỦA SERVICE
            }
            //TẠO APPOINTMENT
            Appointment appointment = new Appointment();

            // SLOT
            Slot slot = slotRepository.findSlotBySlotIdAndIsAvailableTrue(appointmentRequest.getSlotId());
            appointment.setSlot(slot);

            //ACCOUNT FOR CUSTOMER
            AccountForCustomer accountForCustomer = authenticationService.getCurrentAccountForCustomer();
            appointment.setAccountForCustomer(accountForCustomer);

            //HAIR SALON SERVICE
            appointment.setHairSalonServices(hairSalonServiceList);

            //DISCOUNT CODE
            if (!appointmentRequest.getDiscountCode().isEmpty()) {
                DiscountCode discountCode = getDiscountCode(appointmentRequest.getDiscountCode());
                appointment.setDiscountCode(discountCode);
                bonusDiscountCode += (discountCode.getDiscountProgram().getPercentage()) / 100;
            }

            AccountForEmployee accountForEmployee = slot.getShiftEmployee().getAccountForEmployee();
            if (accountForEmployee.getStylistSelectionFee() != 0) {
                bonusEmployee += (accountForEmployee.getStylistSelectionFee()) / 100;
            }

            double totalCost = serviceFee - (bonusDiscountCode * serviceFee) + (bonusEmployee * serviceFee);
            appointment.setCost(totalCost);



            Appointment newAppointment = appointmentRepository.save(appointment);

            //SET OBJ APPOINTMENT VÀO CÁC OBJ KHÁC
            slot.setAppointments(newAppointment);
            slot.setAvailable(false);
            slotRepository.save(slot);

            List<Appointment> appointmentList = accountForCustomer.getAppointments();
            appointmentList.add(newAppointment);
            accountForCustomer.setAppointments(appointmentList);
            accountForCustomerRepository.save(accountForCustomer);

            for(HairSalonService hairSalonService : hairSalonServiceList){
                List<Appointment> appointments = hairSalonService.getAppointments();
                appointments.add(newAppointment);
                hairSalonService.setAppointments(appointments);
                serviceRepository.save(hairSalonService);
            }

            if (!appointmentRequest.getDiscountCode().isEmpty()) {
                DiscountCode discountCode = getDiscountCode(appointmentRequest.getDiscountCode());
                discountCode.setAppointment(newAppointment);
                discountCodeRepository.save(discountCode);
            }

            AppointmentResponse appointmentResponse = new AppointmentResponse();

            appointmentResponse.setId(newAppointment.getAppointmentId());
            appointmentResponse.setCost(newAppointment.getCost());
            appointmentResponse.setDay(newAppointment.getSlot().getDate());
            appointmentResponse.setStartHour(newAppointment.getSlot().getStartSlot());
            appointmentResponse.setCustomer(accountForCustomer.getName());
            appointmentResponse.setService(serviceNameList);
            appointmentResponse.setStylist(newAppointment.getSlot().getShiftEmployee().getAccountForEmployee().getName());

            EmailDetailCreateAppointment emailDetail = new EmailDetailCreateAppointment();
            emailDetail.setReceiver(appointment.getAccountForCustomer());
            emailDetail.setSubject("You have scheduled an appointment at our salon!");
            emailDetail.setAppointmentId(appointmentResponse.getId());
            emailDetail.setServiceName(appointmentResponse.getService());
            emailDetail.setNameStylist(appointmentResponse.getStylist());
            emailDetail.setDay(appointmentResponse.getDay());
            emailDetail.setStartHour(appointmentResponse.getStartHour());
            emailService.sendEmailCreateAppointment(emailDetail);

            return appointmentResponse;
        } catch (Exception e) {
            throw new EntityNotFoundException("Can not create appointment: " + e.getMessage());
        }
    }
/*    //CẬP NHẬT APPOINTMENT -> CUSTOMER LÀM
    // CẬP NHẬT APPOINTMENT CÓ 2 TRƯỜNG HỢP:
    //1. CUSTOMER CHƯA GỬI ĐƠN : TRƯỜNG HỢP NÀY KHÔNG VẤN ĐỀ GÌ, CUSTOMER CÓ THỂ THAO TÁC LẠI CÁC HÀM Ở TRÊN ĐỂ LỰA CHỌN LẠI
    // SAU ĐÓ CHỈNH SỬA ĐẦU VÀO CỦA createNewAppointment LÀ XONG
    //2. CUSTOMER ĐÃ GỬI ĐƠN VÀ MUỐN LẤY LẠI ĐƠN ĐỂ SỬA: CÓ 2 CÁCH GIẢI QUYẾT:
    // A. CUSTOMER HỦY ĐƠN VÀ LÀM LẠI ĐƠN KHÁC
    // B. CUSTOMER LẤY LẠI ĐƠN VÀ UPDATE LẠI THÔNG TIN (TRƯỚC KHI ĐC APPROVE)

    public AppointmentResponse updateAppointment(AppointmentUpdate appointmentUpdate, long id) {
        String status = "Appointment sent!";
        AccountForCustomer accountForCustomer = authenticationService.getCurrentAccountForCustomer();
        Appointment oldAppointment = appointmentRepository.findAppointmentByIdAndAccountForCustomerAndStatusAndIsDeletedFalse(id, accountForCustomer, status);  //TÌM LẠI APPOINTMENT CŨ
        if (oldAppointment != null) {   // TÌM THẤY
            try{
                double cost = 0;          // TÍNH TOÁN COST TỪ ĐẦU
                double newBonusEmployee = 0;
                double newBonusCode = 0;
                if (appointmentUpdate.getServiceId() != 0) {      // NẾU CUSTOMER CÓ NHẬP ID SERVICE
                    oldAppointment.setHairSalonService(getService(appointmentUpdate.getServiceId()));       // CẬP NHẬT
                    //cost += serviceRepository.findHairSalonServiceByIdAndIsAvailableTrue(appointmentUpdate.getServiceId()).getCost(); // CẬP NHẬT LẠI VÀO COST
                    cost += oldAppointment.getHairSalonService().getCost();
                } else {                                        // NẾU CUSTOMER KHÔNG NHẬP ID SERVICE
                    cost += oldAppointment.getHairSalonService().getCost();  //  COST SERVICE NHƯ CŨ
                }

                if (appointmentUpdate.getSlotId() != 0) {         // NẾU CUSTOMER CÓ NHẬP SLOT ID
                    oldAppointment.setSlot(getAvailableSlot(appointmentUpdate.getSlotId()));  // CẬP NHẬT
                }

                if (!appointmentUpdate.getStylistId().isEmpty()) {       // NẾU CUSTOMER NHẬP STYLIST ID -> STYLIST MỚI

                    if (getStylist(appointmentUpdate.getStylistId()).getExpertStylistBonus() != 0) {    // NẾU STYLIST MỚI LÀ DÂN CHUYÊN
                        AccountForEmployee accountForEmployee = getStylist(appointmentUpdate.getStylistId());
                        newBonusEmployee += (accountForEmployee.getExpertStylistBonus()) / 100;   // CẬP NHẬT BONUS CHO NÓ
                    }

                } else {                                                                        // NẾU CUSTOMER KO NHẬP -> STYLIST CŨ
                    String oldStylistId = oldAppointment.getSlot().getShiftEmployee().getAccountForEmployee().getId();
                    AccountForEmployee account = getStylist(oldStylistId);

                    if (account.getExpertStylistBonus() != 0) {      // STYLIST CŨ VẪN LÀ DÂN CHUYÊN
                        newBonusEmployee += (account.getExpertStylistBonus()) / 100;
                    }

                }

                if (!appointmentUpdate.getDiscountCodeId().isEmpty()) {        //  CUSTOMER NHẬP MÃ MỚI

                    DiscountCode newCode = getDiscountCode(appointmentUpdate.getDiscountCodeId());
                    DiscountProgram discountProgram = newCode.getDiscountProgram();
                    newBonusCode += (discountProgram.getPercentage()) / 100;

                } else {      // MÃ CŨ

                    DiscountCode oldCode = oldAppointment.getDiscountCode();
                    DiscountProgram oldProgram = oldCode.getDiscountProgram();
                    newBonusCode += (oldProgram.getPercentage()) / 100;

                }

                double newCost = cost + (newBonusEmployee * cost) + (newBonusCode * cost);
                oldAppointment.setCost(newCost);

                Appointment newAppointment = appointmentRepository.save(oldAppointment);     // LƯU LẠI LÊN DB

                //AppointmentResponse appointmentResponse = modelMapper.map(newAppointment, AppointmentResponse.class);
                appointmentResponse.setServiceId(newAppointment.getHairSalonService().getId());
                appointmentResponse.setCustomerId(newAppointment.getAccountForCustomer().getPhoneNumber());
                appointmentResponse.setSlotId(newAppointment.getSlot().getId());
                appointmentResponse.setDiscountCodeId(newAppointment.getDiscountCode().getId());

                AppointmentResponse appointmentResponse = new AppointmentResponse();

                appointmentResponse.setId(newAppointment.getId());
                appointmentResponse.setCost(newAppointment.getCost());
                appointmentResponse.setDay(newAppointment.getSlot().getShiftEmployee().getShiftInWeek().getDayOfWeek());
                appointmentResponse.setStartHour(newAppointment.getSlot().getStartSlot());
                appointmentResponse.setCustomer(newAppointment.getAccountForCustomer().getCustomerName());
                appointmentResponse.setService(newAppointment.getHairSalonService().getName());
                appointmentResponse.setStylist(newAppointment.getSlot().getShiftEmployee().getName());
                appointmentResponse.setStatus(newAppointment.getStatus());

                return appointmentResponse;
            } catch (Exception e) {
                throw new EntityNotFoundException("Can not update appointment: " + e.getMessage());
            }
        } else {
            throw new EntityNotFoundException("Appointment not found!");
        }
    }*/

    // XÓA APPOINTMENT  -> STAFF LÀM KHI STYLIST CÓ VIỆC BẬN TRONG SLOT ĐÓ
    public String deleteAppointmentByStaff(long slotId){
        Appointment oldAppointment = appointmentRepository
                .findAppointmentBySlot_SlotIdAndIsDeletedFalse(slotId);  //TÌM LẠI APPOINTMENT CŨ
        if(oldAppointment != null){
            oldAppointment.setDeleted(true);
            Appointment newAppointment = appointmentRepository.save(oldAppointment);     // LƯU LẠI LÊN DB

            //SLOT
            Slot slot = newAppointment.getSlot();
            slot.setAppointments(null);
            slot.setAvailable(false);
            slotRepository.save(slot);

            //DISCOUNT CODE
            DiscountCode discountCode = newAppointment.getDiscountCode();
            if(discountCode != null){
                discountCode.setAppointment(null);
                discountCodeRepository.save(discountCode);
            }

            String phoneNumber = newAppointment.getAccountForCustomer().getPhoneNumber();
            String email = newAppointment.getAccountForCustomer().getEmail();

            EmailDetailDeleteAppointment emailDetail = new EmailDetailDeleteAppointment();
            emailDetail.setReceiver(newAppointment.getAccountForCustomer());
            emailDetail.setSubject("You have canceled scheduled an appointment at our salon!");
            emailDetail.setAppointmentId(newAppointment.getAppointmentId());
            emailDetail.setServiceName(newAppointment.getHairSalonServices());
            emailDetail.setNameStylist(newAppointment.getSlot().getShiftEmployee().getAccountForEmployee().getName());
            emailDetail.setDay(newAppointment.getDate());
            emailDetail.setStartHour(newAppointment.getSlot().getStartSlot());
            emailService.sendEmailChangedAppointment(emailDetail);

            String message = "Delete successfully: " + "Phone = " + phoneNumber + "; Email = " + email;
            return message;

        } else {
            throw new EntityNotFoundException("Appointment not found!");
        }
    }

    // XÓA APPOINTMENT -> CUSTOMER LÀM
    public String deleteAppointmentByCustomer(long idAppointment){
        AccountForCustomer accountForCustomer = authenticationService.getCurrentAccountForCustomer();
        Appointment oldAppointment = appointmentRepository
                .findAppointmentByAppointmentIdAndAccountForCustomerAndIsDeletedFalse(idAppointment, accountForCustomer);  //TÌM LẠI APPOINTMENT CŨ
        if(oldAppointment != null){
            oldAppointment.setDeleted(true);
            Appointment newAppointment = appointmentRepository.save(oldAppointment);     // LƯU LẠI LÊN DB

            //SLOT
            Slot slot = newAppointment.getSlot();
            slot.setAppointments(null);
            slot.setAvailable(true);
            slotRepository.save(slot);

            //DISCOUNT CODE
            DiscountCode discountCode = newAppointment.getDiscountCode();
            if(discountCode != null){
                discountCode.setAppointment(null);
                discountCodeRepository.save(discountCode);
            }

            EmailDetailDeleteAppointment emailDetail = new EmailDetailDeleteAppointment();
            emailDetail.setReceiver(newAppointment.getAccountForCustomer());
            emailDetail.setSubject("You have canceled scheduled an appointment at our salon!");
            emailDetail.setAppointmentId(newAppointment.getAppointmentId());
            emailDetail.setServiceName(newAppointment.getHairSalonServices());
            emailDetail.setNameStylist(newAppointment.getSlot().getShiftEmployee().getAccountForEmployee().getName());
            emailDetail.setDay(newAppointment.getDate());
            emailDetail.setStartHour(newAppointment.getSlot().getStartSlot());
            emailService.sendEmailChangedAppointment(emailDetail);

            String message = "Delete successfully!!!";
            return message;

        } else {
            throw new EntityNotFoundException("Appointment not found!");
        }
    }
    // CÓ 2 TÌNH HUỐNG KHI XÓA:
    //- CUSTOMER XÓA TRƯỚC , STAFF XÓA SAU -> KO VẤN ĐỀ VÌ STATUS STAFF CHÈN LÊN CUSTOMER
    //- STAFF XÓA TRƯỚC , SLOT ĐÓ COI NHƯ KO KHẢ DỤNG -> CUSTOMER CHẮC CHẮN KO CHỌN -> OK

    // NẾU CÓ VẤN ĐỀ ĐỘT XUẤT, STAFF GỬI EMAIL ĐẾN CUSTOMER
    // STAFF XÓA CÁC APPOINMENTS NẾU STYLIST NHẬN APPOINTMENT ĐÓ BẬN TRONG NGÀY
    public List<String> deleteAppointmentsOfStylist(DeleteAllAppointmentsRequest deleteAllAppointmentsRequest){
        List<AvailableSlot> availableSlotList = shiftEmployeeService.getAllAvailableSlots(deleteAllAppointmentsRequest.getDate()); // TÌM CÁC SLOT TRONG NGÀY
        List<String> messages = new ArrayList<>();
        if(availableSlotList != null){
            for(AvailableSlot availableSlot : availableSlotList) {
                Slot slot = slotRepository.findSlotBySlotIdAndIsAvailableFalse(availableSlot.getSlotId());  // TÌM SLOT KO KHẢ DỤNG
                if(slot != null){
                    if(slot.getShiftEmployee().getAccountForEmployee().getEmployeeId().equals(deleteAllAppointmentsRequest.getStylistId())){
                        String message = deleteAppointmentByStaff(slot.getSlotId());
                        messages.add(message);
                    }
                }
            }


            return messages;
        } else {
            throw new EntityNotFoundException("Slots not found!");
        }
    }

    // CUSTOMER XEM LẠI LỊCH SỬ APPOINTMENT
    public List<AppointmentResponse> checkAppointmentHistory(){
        AccountForCustomer accountForCustomer = authenticationService.getCurrentAccountForCustomer();
        List<Appointment> appointmentList = accountForCustomer.getAppointments();
        if(appointmentList != null){
            List<AppointmentResponse> appointmentResponseList = new ArrayList<>();
            for(Appointment appointment : appointmentList){
                AppointmentResponse appointmentResponse = new AppointmentResponse();

                appointmentResponse.setId(appointment.getAppointmentId());
                appointmentResponse.setCost(appointment.getCost());
                appointmentResponse.setDay(appointment.getSlot().getDate());
                appointmentResponse.setStartHour(appointment.getSlot().getStartSlot());
                appointmentResponse.setCustomer(accountForCustomer.getName());

                List<String> serviceNameList = new ArrayList<>();
                List<HairSalonService> hairSalonServiceList = appointment.getHairSalonServices();
                for(HairSalonService service : hairSalonServiceList) {
                    String serviceName = service.getName();
                    serviceNameList.add(serviceName);
                }
                appointmentResponse.setService(serviceNameList);
                appointmentResponse.setStylist(appointment.getSlot().getShiftEmployee().getAccountForEmployee().getName());

                appointmentResponseList.add(appointmentResponse);
            }
            return appointmentResponseList;
        } else {
            throw new EntityNotFoundException("Appointment not found!");
        }
    }


    //CUSTOMER TÍNH TIỀN, STAFF CHECK CHO APPOINTMENT
    public Appointment completeAppointment(CompleteAppointmentRequest completeAppointmentRequest) {
        Slot slot = slotRepository.findSlotByStartSlotAndShiftEmployee_AccountForEmployee_EmployeeIdAndDate(
                completeAppointmentRequest.getStartSlot(),
                completeAppointmentRequest.getStylistId(),
                completeAppointmentRequest.getDate()
        );

        if (slot != null) {
            Appointment appointment = slot.getAppointments();
            if (appointment != null) {
                appointment.setCompleted(true);
                appointmentRepository.save(appointment);

                AccountForEmployee account = slot.getShiftEmployee().getAccountForEmployee();
                if (account != null) {
                    account.setKPI(account.getKPI() + 1);
                    employeeRepository.save(account);
                }
                return appointment;
            } else {
                throw new EntityNotFoundException("Appointment not found!");
            }
        } else {
            throw new EntityNotFoundException("Slot not found!");
        }
    }

//    public String completeAppointment(CompleteAppointmentRequest completeAppointmentRequest){
//        Slot slot = slotRepository
//                .findSlotByStartSlotAndShiftEmployee_AccountForEmployee_EmployeeIdAndDate(
//                        completeAppointmentRequest.getStartSlot(),
//                        completeAppointmentRequest.getStylistId(),
//                        completeAppointmentRequest.getDate()
//                );
//        if(slot != null){
//            Appointment appointment = slot.getAppointments();
//            appointment.setCompleted(true);
//            appointmentRepository.save(appointment);
//
//            AccountForEmployee account = slot.getShiftEmployee().getAccountForEmployee();
//            account.setKPI(account.getKPI() + 1);
//            employeeRepository.save(account);
//
//            /*slot.setAppointments(null);
//            slot.setAvailable(true);
//            slotRepository.save(slot);*/ // KHI TÍNH CHECK COMPLETE SLOT CÓ NGHĨA SLOT ĐÓ ĐÃ QUA RỒI, KO CÒN DÙNG NỮA
//
//            String message = "Complete successfully!!!";
//            return message;
//        } else {
//            throw new EntityNotFoundException("Slot not found!");
//        }
//    }

    // DANH SÁCH CÁC STYLIST KHẢ DỤNG -> HỖ TRỢ HÀM DƯỚI
    public List<AccountForEmployee> getAllStylistList(){
        String role = "Stylist";
        String status = "Workday";
        List<AccountForEmployee> list = employeeRepository.findAccountForEmployeesByRoleAndStatusAndIsDeletedFalse(role, status);
        if(list != null){
            return list;
        } else {
            throw new EntityNotFoundException("Stylist not found!");
        }
    }

    // HÀM TRẢ VỀ DANH SÁCH CÁC STYLIST VÀ KPI
    public List<KPITotal> getAllKPI(){
        List<KPITotal> kpiTotalList = new ArrayList<>();
        for(AccountForEmployee account : getAllStylistList()){
            KPITotal kpiTotal = new KPITotal();
            kpiTotal.setStylistId(account.getEmployeeId());
            kpiTotal.setKPI(account.getKPI());
            kpiTotal.setTargetKPI(account.getTargetKPI());

            kpiTotalList.add(kpiTotal);
            account.setKPI(0);
            employeeRepository.save(account);
        }
        return kpiTotalList;
    }

    // UPDATE APPOINTMENT ->  CUSTOMER LÀM
    public AppointmentResponse updateAppointment(AppointmentUpdate appointmentUpdate, long idAppointment){
        AccountForCustomer accountForCustomer = authenticationService.getCurrentAccountForCustomer();
        Appointment oldAppointment = appointmentRepository
                .findAppointmentByAppointmentIdAndAccountForCustomerAndIsDeletedFalse(idAppointment, accountForCustomer);
        if(oldAppointment == null){
            throw new EntityNotFoundException("Appointment not found!!!");
        }
        // XÓA APPOINTMENT CŨ
        deleteAppointmentByCustomer(oldAppointment.getAppointmentId());
        // TẠO APPOINTMENT MỚI
        AppointmentRequest appointmentRequest = new AppointmentRequest();
        long newSlotId = appointmentUpdate.getSlotId();
        List<Long> newServiceIdList = appointmentUpdate.getServiceIdList();
        String newCode = appointmentUpdate.getDiscountCode();
        if(newSlotId != 0){
            appointmentRequest.setSlotId(newSlotId);
        }
        if(!newServiceIdList.isEmpty()){
            appointmentRequest.setServiceIdList(newServiceIdList);
        }
        if(!newCode.isEmpty()){
            appointmentRequest.setDiscountCode(newCode);
        }




        return createNewAppointment(appointmentRequest);
    }


}
