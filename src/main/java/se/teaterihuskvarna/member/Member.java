package se.teaterihuskvarna.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/// A member of the association.
///
/// Column lengths mirror `V1__member_register.sql` by hand. Nothing enforces
/// that: `ddl-auto: validate` catches a missing table or column, but it does not
/// compare lengths, which was measured rather than assumed. See
/// `docs/decisions/0012-jpa-over-a-schema-flyway-owns.md`.
///
/// No personal identity number, by `docs/projektplan.md`.
@Entity
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id")
    private Household household;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "address", length = 200)
    private String address;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Member() {
        // for JPA
    }

    /// @param fullName the member's name as the association writes it
    /// @param email     the address login links and mailings go to, unique case insensitively
    public Member(String fullName, String email) {
        this.fullName = fullName;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    /// @param fullName the new name
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    /// @param email the new address, unique case insensitively across the register
    public void setEmail(String email) {
        this.email = email;
    }

    public Household getHousehold() {
        return household;
    }

    /// @param household the household whose fee covers this member, or null for none
    public void setHousehold(Household household) {
        this.household = household;
    }

    public String getPhone() {
        return phone;
    }

    /// @param phone the new phone number
    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    /// @param address the new street address
    public void setAddress(String address) {
        this.address = address;
    }

    public String getPostalCode() {
        return postalCode;
    }

    /// @param postalCode the new postal code
    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCity() {
        return city;
    }

    /// @param city the new city
    public void setCity(String city) {
        this.city = city;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
