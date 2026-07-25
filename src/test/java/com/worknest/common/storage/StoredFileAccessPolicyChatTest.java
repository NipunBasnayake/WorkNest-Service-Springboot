package com.worknest.common.storage;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.security.model.PlatformUserPrincipal;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.HrConversation;
import com.worknest.tenant.entity.HrMessage;
import com.worknest.tenant.entity.StoredFileMetadata;
import com.worknest.tenant.entity.Team;
import com.worknest.tenant.entity.TeamChat;
import com.worknest.tenant.entity.TeamChatMessage;
import com.worknest.tenant.repository.AnnouncementRepository;
import com.worknest.tenant.repository.HrMessageRepository;
import com.worknest.tenant.repository.LeaveRequestRepository;
import com.worknest.tenant.repository.ProjectRepository;
import com.worknest.tenant.repository.TaskRepository;
import com.worknest.tenant.repository.TeamChatMessageRepository;
import com.worknest.tenant.repository.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoredFileAccessPolicyChatTest {

    @Mock private SecurityUtils securityUtils;
    @Mock private AuthorizationService authorizationService;
    @Mock private TaskRepository taskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private LeaveRequestRepository leaveRequestRepository;
    @Mock private AnnouncementRepository announcementRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private TeamChatMessageRepository teamChatMessageRepository;
    @Mock private HrMessageRepository hrMessageRepository;

    private StoredFileAccessPolicy policy;
    private Employee viewer;

    @BeforeEach
    void setUp() {
        policy = new StoredFileAccessPolicy(
                securityUtils,
                authorizationService,
                taskRepository,
                projectRepository,
                leaveRequestRepository,
                announcementRepository,
                teamMemberRepository,
                teamChatMessageRepository,
                hrMessageRepository);
        PlatformUserPrincipal principal = mock(PlatformUserPrincipal.class);
        lenient().when(principal.getRole()).thenReturn(PlatformRole.EMPLOYEE);
        lenient().when(securityUtils.getCurrentPrincipalOrThrow()).thenReturn(principal);
        lenient().when(authorizationService.hasPermission(Permission.CHAT_ACCESS)).thenReturn(true);
        lenient().when(authorizationService.getCurrentRoleOrThrow()).thenReturn(PlatformRole.EMPLOYEE);
        viewer = employee(10L);
        lenient().when(authorizationService.getCurrentEmployeeOrNull()).thenReturn(viewer);
    }

    @Test
    void readBoundaryIsExplicitlyTransactional() throws Exception {
        Transactional annotation = StoredFileAccessPolicy.class
                .getMethod("requireRead", StoredFileMetadata.class)
                .getAnnotation(Transactional.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.readOnly()).isTrue();
        assertThat(annotation.transactionManager()).isEqualTo("transactionManager");
    }

    @Test
    void hrChatParticipantCanReadUsingAccessContextGraphQuery() {
        HrConversation conversation = new HrConversation();
        conversation.setEmployee(viewer);
        conversation.setHr(employee(20L));
        HrMessage message = new HrMessage();
        message.setId(101L);
        message.setConversation(conversation);
        message.setSender(employee(20L));
        when(hrMessageRepository.findWithAccessContextById(101L)).thenReturn(Optional.of(message));

        assertThatCode(() -> policy.requireRead(chatFile("HR_CHAT_MESSAGE", 101L))).doesNotThrowAnyException();
        verify(hrMessageRepository).findWithAccessContextById(101L);
    }

    @Test
    void unrelatedEmployeeCannotReadHrChatAttachment() {
        HrConversation conversation = new HrConversation();
        conversation.setEmployee(employee(11L));
        conversation.setHr(employee(20L));
        HrMessage message = new HrMessage();
        message.setConversation(conversation);
        message.setSender(employee(20L));
        when(hrMessageRepository.findWithAccessContextById(102L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> policy.requireRead(chatFile("HR_CHAT_MESSAGE", 102L)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void activeTeamMemberCanReadTeamChatAttachment() {
        Team team = new Team();
        team.setId(55L);
        team.setManager(employee(90L));
        TeamChat chat = new TeamChat();
        chat.setTeam(team);
        TeamChatMessage message = new TeamChatMessage();
        message.setTeamChat(chat);
        message.setSender(employee(12L));
        when(teamChatMessageRepository.findWithAccessContextById(103L)).thenReturn(Optional.of(message));
        when(teamMemberRepository.findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(55L, 10L))
                .thenReturn(Optional.of(mock(com.worknest.tenant.entity.TeamMember.class)));

        assertThatCode(() -> policy.requireRead(chatFile("TEAM_CHAT_MESSAGE", 103L))).doesNotThrowAnyException();
        verify(teamChatMessageRepository).findWithAccessContextById(103L);
    }

    private StoredFileMetadata chatFile(String module, Long messageId) {
        StoredFileMetadata file = new StoredFileMetadata();
        file.setStorageCategory(StorageCategory.CHAT_ATTACHMENT);
        file.setRelatedModule(module);
        file.setRelatedEntityId(messageId);
        return file;
    }

    private Employee employee(Long id) {
        Employee employee = new Employee();
        employee.setId(id);
        return employee;
    }
}
