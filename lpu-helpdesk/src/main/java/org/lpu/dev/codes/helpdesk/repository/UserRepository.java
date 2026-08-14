package org.lpu.dev.codes.helpdesk.repository;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.lpu.dev.codes.helpdesk.model.Role;
import org.lpu.dev.codes.helpdesk.model.User;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserRepository {

    private static final Set<Role> STAFF_ROLES = EnumSet.of(Role.ADMIN, Role.SUPER_ADMIN, Role.MONITORING);

    private final SessionFactory sessionFactory;

    public UserRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    /** End-user (OTP / Microsoft) account by email. Staff accounts with the same email are ignored. */
    @Transactional(readOnly = true)
    public Optional<User> findUserByEmail(String email) {
        return currentSession()
                .createQuery(
                        "FROM User u WHERE lower(u.email) = lower(:email) AND u.role = :role",
                        User.class
                )
                .setParameter("email", email)
                .setParameter("role", Role.USER)
                .uniqueResultOptional();
    }

    /** Staff account by email (ADMIN / SUPER_ADMIN / MONITORING). */
    @Transactional(readOnly = true)
    public Optional<User> findStaffByEmail(String email) {
        return currentSession()
                .createQuery(
                        "FROM User u WHERE lower(u.email) = lower(:email) AND u.role IN :roles",
                        User.class
                )
                .setParameter("email", email)
                .setParameter("roles", STAFF_ROLES)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return currentSession()
                .createQuery("FROM User u WHERE lower(u.username) = lower(:username)", User.class)
                .setParameter("username", username)
                .uniqueResultOptional();
    }

    /** Staff login: match email or username (case-insensitive), staff roles only. */
    @Transactional(readOnly = true)
    public Optional<User> findStaffByEmailOrUsername(String login) {
        return currentSession()
                .createQuery(
                        """
                        FROM User u
                        WHERE u.role IN :roles
                          AND (lower(u.email) = lower(:login) OR lower(u.username) = lower(:login))
                        """,
                        User.class
                )
                .setParameter("roles", STAFF_ROLES)
                .setParameter("login", login)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return currentSession()
                .createQuery("FROM User u WHERE u.id = :id", User.class)
                .setParameter("id", id)
                .uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public List<User> findByRoleIn(List<Role> roles) {
        return currentSession()
                .createQuery("FROM User u WHERE u.role IN :roles ORDER BY u.name ASC", User.class)
                .setParameter("roles", roles)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public Map<Long, User> findByIdIn(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<User> users = currentSession()
                .createQuery("FROM User u WHERE u.id IN :ids", User.class)
                .setParameter("ids", ids)
                .getResultList();
        return users.stream().collect(Collectors.toMap(User::getId, u -> u));
    }

    @Transactional
    public User persist(User user) {
        Session session = currentSession();
        session.persist(user);
        session.flush();
        return user;
    }

    @Transactional
    public User save(User user) {
        return currentSession().merge(user);
    }
}
