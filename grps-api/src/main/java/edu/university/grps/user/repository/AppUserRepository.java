package edu.university.grps.user.repository;

import edu.university.grps.user.domain.AppUser;
import edu.university.grps.user.domain.RoleCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    @Query("select u from AppUser u where lower(u.email) = lower(:login) or lower(u.staffOrStudentNo) = lower(:login)")
    Optional<AppUser> findByLogin(@Param("login") String login);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select count(u) > 0 from AppUser u where lower(u.staffOrStudentNo) = lower(:number)")
    boolean existsByStaffOrStudentNoIgnoreCase(@Param("number") String number);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    @Query("select distinct u from AppUser u join u.roles r where r.code = :role and u.active = true")
    List<AppUser> findActiveByRole(@Param("role") RoleCode role);

    @Query("""
            select u from AppUser u
            where (:role is null or exists
                   (select 1 from AppUser x join x.roles xr where x = u and xr.code = :role))
              and (:q = '' or lower(u.firstName) like :q or lower(u.lastName) like :q
                   or lower(u.email) like :q or lower(u.staffOrStudentNo) like :q)
              and (:staffOnly = false or not exists
                   (select 1 from AppUser y join y.roles yr where y = u
                    and yr.code = edu.university.grps.user.domain.RoleCode.STUDENT))
            """)
    Page<AppUser> search(@Param("q") String q, @Param("role") RoleCode role,
                         @Param("staffOnly") boolean staffOnly, Pageable pageable);
}
