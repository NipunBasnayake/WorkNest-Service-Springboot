package com.worknest.config;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.TenantStatus;
import com.worknest.common.enums.UserStatus;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.master.repository.PlatformUserRepository;
import com.worknest.tenant.context.MasterTenantContextRunner;
import com.worknest.tenant.context.TenantContext;
import com.worknest.tenant.entity.*;
import com.worknest.tenant.enums.ProjectStatus;
import com.worknest.tenant.enums.NotificationType;
import com.worknest.tenant.enums.TaskPriority;
import com.worknest.tenant.enums.TaskStatus;
import com.worknest.tenant.enums.TeamFunctionalRole;
import com.worknest.tenant.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@Order(30)
@ConditionalOnProperty(name = "bootstrap.seed-demo-data", havingValue = "true")
public class DemoTenantDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoTenantDataSeeder.class);

    private final PlatformTenantRepository platformTenantRepository;
    private final PlatformUserRepository platformUserRepository;
    private final MasterTenantContextRunner masterTenantContextRunner;
    private final EmployeeRepository employeeRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ProjectRepository projectRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final TaskRepository taskRepository;
    private final AnnouncementRepository announcementRepository;
    private final NotificationRepository notificationRepository;
    private final TeamChatRepository teamChatRepository;
    private final TeamChatMessageRepository teamChatMessageRepository;
    private final HrConversationRepository hrConversationRepository;
    private final HrMessageRepository hrMessageRepository;
    private final PasswordEncoder passwordEncoder;
    private final String demoUserPassword;

    public DemoTenantDataSeeder(
            PlatformTenantRepository platformTenantRepository,
            PlatformUserRepository platformUserRepository,
            MasterTenantContextRunner masterTenantContextRunner,
            EmployeeRepository employeeRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            ProjectRepository projectRepository,
            ProjectTeamRepository projectTeamRepository,
            TaskRepository taskRepository,
            AnnouncementRepository announcementRepository,
            NotificationRepository notificationRepository,
            TeamChatRepository teamChatRepository,
            TeamChatMessageRepository teamChatMessageRepository,
            HrConversationRepository hrConversationRepository,
            HrMessageRepository hrMessageRepository,
            PasswordEncoder passwordEncoder,
            @Value("${bootstrap.demo-user-password:}") String demoUserPassword) {
        this.platformTenantRepository = platformTenantRepository;
        this.platformUserRepository = platformUserRepository;
        this.masterTenantContextRunner = masterTenantContextRunner;
        this.employeeRepository = employeeRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.projectRepository = projectRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.taskRepository = taskRepository;
        this.announcementRepository = announcementRepository;
        this.notificationRepository = notificationRepository;
        this.teamChatRepository = teamChatRepository;
        this.teamChatMessageRepository = teamChatMessageRepository;
        this.hrConversationRepository = hrConversationRepository;
        this.hrMessageRepository = hrMessageRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoUserPassword = demoUserPassword;
    }

    @Override
    public void run(String... args) {
        List<PlatformTenant> activeTenants = masterTenantContextRunner.runInMasterContext(() ->
                platformTenantRepository.findAll().stream()
                        .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                        .toList()
        );

        for (PlatformTenant tenant : activeTenants) {
            seedTenantData(tenant);
        }
    }

    private void seedTenantData(PlatformTenant tenant) {
        String previousTenant = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenant.getTenantKey());

            if (employeeRepository.count() > 0) {
                log.info("Skipping demo seed for tenant {} because employees already exist", tenant.getTenantKey());
                return;
            }
            if (demoUserPassword == null || demoUserPassword.isBlank()) {
                throw new IllegalStateException(
                        "Demo tenant seeding is enabled but bootstrap.demo-user-password is not configured");
            }

            Employee admin = createEmployee(tenant.getTenantKey(), "ADM", "Tenant", "Admin", PlatformRole.ADMIN);
            Employee manager = createEmployee(tenant.getTenantKey(), "MGR", "Project", "Manager", PlatformRole.MANAGER);
            Employee hr = createEmployee(tenant.getTenantKey(), "HR", "People", "Ops", PlatformRole.HR);
            Employee engineer = createEmployee(tenant.getTenantKey(), "EMP", "Team", "Member", PlatformRole.EMPLOYEE);

            Team team = new Team();
            team.setName("Core Delivery Team");
            team.setManager(manager);
            Team savedTeam = teamRepository.save(team);

            teamMemberRepository.save(createTeamMember(savedTeam, manager, TeamFunctionalRole.TEAM_LEAD));
            teamMemberRepository.save(createTeamMember(savedTeam, engineer, TeamFunctionalRole.DEVELOPER));

            Project project = new Project();
            project.setName("Demo Platform Project");
            project.setDescription("Demo seeded project for Phase 3 flow validation");
            project.setStartDate(LocalDate.now().minusDays(7));
            project.setEndDate(LocalDate.now().plusDays(30));
            project.setStatus(ProjectStatus.IN_PROGRESS);
            project.setCreatedBy(admin);
            Project savedProject = projectRepository.save(project);

            ProjectTeam projectTeam = new ProjectTeam();
            projectTeam.setProject(savedProject);
            projectTeam.setTeam(savedTeam);
            projectTeamRepository.save(projectTeam);

            Task task = new Task();
            task.setProject(savedProject);
            task.setTitle("Seeded demo task");
            task.setDescription("This task is generated by demo seeder");
            task.setStatus(TaskStatus.TODO);
            task.setPriority(TaskPriority.MEDIUM);
            task.setAssignee(engineer);
            task.setCreatedBy(manager);
            task.setDueDate(LocalDate.now().plusDays(5));
            taskRepository.save(task);

            Announcement announcement = new Announcement();
            announcement.setTitle("Welcome to " + tenant.getCompanyName());
            announcement.setMessage("Demo announcement generated for API validation.");
            announcement.setCreatedBy(admin);
            Announcement savedAnnouncement = announcementRepository.save(announcement);

            Notification notification = new Notification();
            notification.setRecipient(engineer);
            notification.setType(NotificationType.ANNOUNCEMENT);
            notification.setMessage("New announcement: " + savedAnnouncement.getTitle());
            notification.setReferenceType("ANNOUNCEMENT");
            notification.setReferenceId(savedAnnouncement.getId());
            notification.setRead(false);
            notificationRepository.save(notification);

            TeamChat teamChat = new TeamChat();
            teamChat.setTeam(savedTeam);
            TeamChat savedTeamChat = teamChatRepository.save(teamChat);

            TeamChatMessage teamChatMessage = new TeamChatMessage();
            teamChatMessage.setTeamChat(savedTeamChat);
            teamChatMessage.setSender(manager);
            teamChatMessage.setMessage("Welcome team. Use this chat for project updates.");
            teamChatMessageRepository.save(teamChatMessage);

            HrConversation hrConversation = new HrConversation();
            hrConversation.setEmployee(engineer);
            hrConversation.setHr(hr);
            HrConversation savedConversation = hrConversationRepository.save(hrConversation);

            HrMessage hrMessage = new HrMessage();
            hrMessage.setConversation(savedConversation);
            hrMessage.setSender(engineer);
            hrMessage.setMessage("Hello HR, this is a seeded private chat message.");
            hrMessage.setRead(false);
            hrMessageRepository.save(hrMessage);

            log.info("Demo seed completed for tenant {}", tenant.getTenantKey());
        } finally {
            if (previousTenant == null || previousTenant.isBlank()) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previousTenant);
            }
        }
    }

    private Employee createEmployee(
            String tenantKey,
            String codePrefix,
            String firstName,
            String lastName,
            PlatformRole role) {

        Employee employee = new Employee();
        employee.setEmployeeCode(codePrefix + "-" + tenantKey.toUpperCase());
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setEmail(role.name().toLowerCase() + "." + tenantKey + "@demo.worknest.local");
        employee.setPasswordHash(passwordEncoder.encode(demoUserPassword));
        employee.setRole(role);
        employee.setDesignation(role.name());
        employee.setJoinedDate(LocalDate.now().minusMonths(6));
        employee.setStatus(UserStatus.ACTIVE);

        Employee saved = employeeRepository.save(employee);
        PlatformUser platformUser = ensurePlatformUser(saved, tenantKey);
        saved.setPlatformUserId(platformUser.getId());
        return employeeRepository.save(saved);
    }

    private TeamMember createTeamMember(Team team, Employee employee, TeamFunctionalRole functionalRole) {
        TeamMember teamMember = new TeamMember();
        teamMember.setTeam(team);
        teamMember.setEmployee(employee);
        teamMember.setFunctionalRole(functionalRole);
        return teamMember;
    }

    private PlatformUser ensurePlatformUser(Employee employee, String tenantKey) {
        return masterTenantContextRunner.runInMasterContext(() -> {
            PlatformUser existing = platformUserRepository.findByEmailIgnoreCase(employee.getEmail()).orElse(null);
            if (existing != null) {
                return existing;
            }

            PlatformUser user = new PlatformUser();
            user.setFullName(employee.getFirstName() + " " + employee.getLastName());
            user.setEmail(employee.getEmail());
            user.setPasswordHash(employee.getPasswordHash());
            user.setRole(employee.getRole());
            user.setStatus(employee.getStatus());
            user.setTenantKey(tenantKey);

            return platformUserRepository.save(user);
        });
    }
}
