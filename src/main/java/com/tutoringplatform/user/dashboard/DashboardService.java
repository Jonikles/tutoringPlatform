package com.tutoringplatform.user.dashboard;

import com.tutoringplatform.booking.Booking;
import com.tutoringplatform.booking.IBookingRepository;
import com.tutoringplatform.payment.IPaymentRepository;
import com.tutoringplatform.payment.Payment;
import com.tutoringplatform.review.IReviewRepository;
import com.tutoringplatform.review.Review;
import com.tutoringplatform.shared.dto.response.*;
import com.tutoringplatform.shared.util.DTOMapper;
import com.tutoringplatform.user.student.IStudentRepository;
import com.tutoringplatform.user.exceptions.UserNotFoundException;
import com.tutoringplatform.user.tutor.ITutorRepository;
import com.tutoringplatform.user.tutor.Tutor;
import com.tutoringplatform.user.student.Student;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

@Service
public class DashboardService {

    private final Logger logger = LoggerFactory.getLogger(DashboardService.class);
    private final IStudentRepository studentRepository;
    private final ITutorRepository tutorRepository;
    private final IBookingRepository bookingRepository;
    private final IReviewRepository reviewRepository;
    private final IPaymentRepository paymentRepository;
    private final DTOMapper dtoMapper;

    @Autowired
    public DashboardService(
            IStudentRepository studentRepository,
            ITutorRepository tutorRepository,
            IBookingRepository bookingRepository,
            IReviewRepository reviewRepository,
            IPaymentRepository paymentRepository,
            DTOMapper dtoMapper) {
        this.studentRepository = studentRepository;
        this.tutorRepository = tutorRepository;
        this.bookingRepository = bookingRepository;
        this.reviewRepository = reviewRepository;
        this.paymentRepository = paymentRepository;
        this.dtoMapper = dtoMapper;
    }

    public StudentDashboardResponse getStudentDashboard(String studentId) throws UserNotFoundException {
        // Fetch student
        Student student = studentRepository.findById(studentId);
        if (student == null) {
            logger.error("Student not found: {}", studentId);
            throw new UserNotFoundException(studentId);
        }

        // Build profile
        UserProfile profile = new UserProfile();
        profile.setName(student.getName());
        profile.setBalance(student.getBalance());
        profile.setProfilePictureUrl(dtoMapper.buildProfilePictureUrl(student.getProfilePictureId()));

        // Build stats
        List<Booking> allBookings = bookingRepository.findByStudentId(studentId);
        DashboardStats stats = buildStudentStats(allBookings);

        // Get upcoming bookings with details
        LocalDateTime now = LocalDateTime.now();
        List<BookingDetailResponse> upcomingBookings = new ArrayList<>();

        for (Booking booking : allBookings) {
            if (booking.getDateTime().isAfter(now) &&
                    booking.getStatus() != Booking.BookingStatus.CANCELLED) {

                Tutor tutor = tutorRepository.findById(booking.getTutorId());
                Payment payment = paymentRepository.findByBookingId(booking.getId());

                BookingDetailResponse detail = dtoMapper.toBookingDetailResponse(
                        booking, student, tutor, payment);
                upcomingBookings.add(detail);
            }
        }

        // Sort by date
        upcomingBookings.sort((a, b) -> a.getDateTime().compareTo(b.getDateTime()));

        return dtoMapper.toStudentDashboardResponse(profile, stats, upcomingBookings);
    }

    public TutorDashboardResponse getTutorDashboard(String tutorId) throws UserNotFoundException {
        // Fetch tutor
        Tutor tutor = tutorRepository.findById(tutorId);
        if (tutor == null) {
            logger.error("Tutor not found: {}", tutorId);
            throw new UserNotFoundException(tutorId);
        }

        // Build profile
        UserProfile profile = new UserProfile();
        profile.setName(tutor.getName());
        profile.setHourlyRate(tutor.getHourlyRate());
        profile.setProfilePictureUrl(dtoMapper.buildProfilePictureUrl(tutor.getProfilePictureId()));

        // Build stats
        List<Booking> allBookings = bookingRepository.findByTutorId(tutorId);
        List<Review> allReviews = reviewRepository.getTutorReviews(tutorId);
        DashboardStats stats = buildTutorStats(allBookings, allReviews, tutor.getEarnings());

        // Get upcoming bookings
        LocalDateTime now = LocalDateTime.now();
        List<BookingDetailResponse> upcomingBookings = new ArrayList<>();
        List<BookingDetailResponse> todaysSchedule = new ArrayList<>();

        for (Booking booking : allBookings) {
            if (booking.getDateTime().isAfter(now) &&
                    booking.getStatus() != Booking.BookingStatus.CANCELLED) {

                Student student = studentRepository.findById(booking.getStudentId());
                Payment payment = paymentRepository.findByBookingId(booking.getId());

                BookingDetailResponse detail = dtoMapper.toBookingDetailResponse(
                        booking, student, tutor, payment);

                upcomingBookings.add(detail);

                // Check if it's today
                if (booking.getDateTime().toLocalDate().equals(LocalDate.now())) {
                    todaysSchedule.add(detail);
                }
            }
        }

        // Sort by date
        upcomingBookings.sort((a, b) -> a.getDateTime().compareTo(b.getDateTime()));
        todaysSchedule.sort((a, b) -> a.getDateTime().compareTo(b.getDateTime()));

        return dtoMapper.toTutorDashboardResponse(profile, stats, upcomingBookings, null, todaysSchedule);
    }

    private DashboardStats buildStudentStats(List<Booking> bookings) {
        DashboardStats stats = new DashboardStats();

        int totalSessions = bookings.size();
        int completedSessions = (int) bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.COMPLETED)
                .count();
        int upcomingSessions = (int) bookings.stream()
                .filter(b -> b.getStatus() != Booking.BookingStatus.CANCELLED &&
                        b.getDateTime().isAfter(LocalDateTime.now()))
                .count();

        stats.setTotalSessions(totalSessions);
        stats.setCompletedSessions(completedSessions);
        stats.setUpcomingSessions(upcomingSessions);

        return stats;
    }

    private DashboardStats buildTutorStats(List<Booking> bookings, List<Review> reviews, double totalEarnings) {
        DashboardStats stats = new DashboardStats();

        // Booking stats
        int totalSessions = bookings.size();
        int completedSessions = (int) bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.COMPLETED)
                .count();
        int upcomingSessions = (int) bookings.stream()
                .filter(b -> b.getStatus() != Booking.BookingStatus.CANCELLED &&
                        b.getDateTime().isAfter(LocalDateTime.now()))
                .count();

        // This month's earnings
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        double thisMonthEarnings = bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.COMPLETED &&
                        b.getDateTime().toLocalDate().isAfter(startOfMonth.minusDays(1)))
                .mapToDouble(Booking::getTotalCost)
                .sum();

        // Review stats
        double averageRating = reviews.isEmpty() ? 0.0
                : reviews.stream()
                        .mapToDouble(Review::getRating)
                        .average()
                        .orElse(0.0);

        stats.setTotalSessions(totalSessions);
        stats.setCompletedSessions(completedSessions);
        stats.setUpcomingSessions(upcomingSessions);
        stats.setTotalEarnings(totalEarnings);
        stats.setThisMonthEarnings(thisMonthEarnings);
        stats.setAverageRating(averageRating);
        stats.setTotalReviews(reviews.size());

        return stats;
    }
}