# \# Library Management System (LMS)

# 

# A Library Management System developed as a group project to manage library operations, users, resources, and administrative activities.

# 

# This repository contains the different modules developed as part of the LMS project.

# 

# \---

# 

# \## User \& Access Management

# 

# The \*\*User \& Access Management\*\* module is responsible for user authentication, account management, role-based access control, member administration, and account security within the Library Management System.

# 

# \### Main Features

# 

# \- User registration

# \- User login and logout

# \- User account management

# \- Role-based access control

# \- Member approval and rejection

# \- Member suspension

# \- Password reset functionality

# \- Failed login attempt tracking

# \- Access-denied handling

# \- Secure password hashing

# \- Audit logging for security-related activities

# 

# \---

# 

# \## User Registration

# 

# New users can create an account through the registration page.

# 

# The registration process validates the information entered by the user before creating the account. Duplicate registration information is also checked to help prevent duplicate user accounts.

# 

# Main components:

# 

# \- `RegistrationController.java`

# \- `RegistrationService.java`

# \- `RegistrationForm.java`

# \- `DuplicateRegistrationException.java`

# \- `register.html`

# 

# \---

# 

# \## Authentication

# 

# Registered users can log in using their credentials.

# 

# Authentication and authorization are managed through the application's security configuration. After successful authentication, access to protected functionality is determined according to the user's assigned roles.

# 

# The system also handles successful and unsuccessful login attempts.

# 

# Main components:

# 

# \- `LoginController.java`

# \- `LoginSuccessHandler.java`

# \- `LoginFailureHandler.java`

# \- `LoginAttemptService.java`

# \- `AppUserDetailsService.java`

# \- `AppUserPrincipal.java`

# \- `SecurityConfig.java`

# \- `login.html`

# 

# \---

# 

# \## Role-Based Access Control

# 

# The system uses role-based access control to restrict functionality based on the authenticated user's assigned role.

# 

# Users and roles are connected through the user-role relationship.

# 

# Related domain components include:

# 

# \- `Role.java`

# \- `RoleRepository.java`

# \- `UserRole.java`

# \- `UserRoleId.java`

# 

# This structure allows different types of users to receive appropriate access to protected areas of the Library Management System.

# 

# \---

# 

# \## Member Administration

# 

# Authorized users can manage library members through the member administration functionality.

# 

# Available functionality includes:

# 

# \- View all members

# \- View pending member registrations

# \- Approve members

# \- Reject members

# \- Suspend members

# \- Manage member account status

# 

# Main components:

# 

# \- `MemberAdminController.java`

# \- `MemberAdminService.java`

# \- `MemberAdminException.java`

# \- `Member.java`

# \- `MemberRepository.java`

# \- `MemberType.java`

# \- `MembershipStatus.java`

# 

# Related user interfaces include:

# 

# \- `all-members.html`

# \- `pending-members.html`

# \- `member-fragments.html`

# 

# \---

# 

# \## Account Management

# 

# Authenticated users can access their account page to view their account information.

# 

# Main components:

# 

# \- `AccountController.java`

# \- `AccountService.java`

# \- `AccountView.java`

# \- `account.html`

# 

# \---

# 

# \## Password Reset

# 

# The system provides password reset functionality for users who forget their passwords.

# 

# Password reset requests are handled using password reset tokens. A valid reset token allows the user to create a new password.

# 

# Main components:

# 

# \- `PasswordResetController.java`

# \- `PasswordResetService.java`

# \- `PasswordResetMailer.java`

# \- `PasswordResetToken.java`

# \- `PasswordResetTokenRepository.java`

# \- `ForgotPasswordForm.java`

# \- `ResetPasswordForm.java`

# 

# Related pages:

# 

# \- `forgot-password.html`

# \- `reset-password.html`

# 

# \---

# 

# \## Login Security

# 

# The system includes security functionality for monitoring unsuccessful login attempts.

# 

# Failed login attempts can be recorded for security monitoring and administration.

# 

# Related components:

# 

# \- `FailedLoginAttempt.java`

# \- `FailedLoginAttemptRepository.java`

# \- `FailureReason.java`

# \- `LoginAttemptService.java`

# \- `LoginFailureHandler.java`

# 

# Passwords are stored using secure password hashing instead of plain-text passwords.

# 

# \---

# 

# \## Audit Logging

# 

# The system includes audit logging functionality for recording important activities.

# 

# Audit logs can assist administrators in monitoring security-related and administrative actions performed within the system.

# 

# Related components include:

# 

# \- `AuditLog.java`

# \- `AuditLogRepository.java`

# \- `AuditAction.java`

# \- `AuditAspect.java`

# 

# \---

# 

# \## Validation

# 

# Validation is used to ensure that information submitted through user forms meets the required conditions.

# 

# Custom field-matching validation is also available for fields that must contain matching values, such as password confirmation.

# 

# Related components:

# 

# \- `FieldsMatch.java`

# \- `FieldsMatchValidator.java`

# 

# \---

# 

# \## Access Denied Handling

# 

# Users who attempt to access functionality without the required authorization are directed to an access-denied page.

# 

# Related page:

# 

# `src/main/resources/templates/error/access-denied.html`

# 

# \---

# 

# \## Database

# 

# Database scripts are stored inside the `database` directory.

# 

# ```text

# database/

# ├── 01\_schema.sql

# └── 04\_security\_migration.sql

# ```

# 

# \### `01\_schema.sql`

# 

# Contains the main database schema required by the Library Management System.

# 

# \### `04\_security\_migration.sql`

# 

# Contains database changes related to authentication and security functionality.

# 

# \---

# 

# \## User \& Access Management Project Structure

# 

# ```text

# src/main/java/com/lms/

# │

# ├── user/

# │   ├── AccountController.java

# │   ├── AccountService.java

# │   ├── DuplicateRegistrationException.java

# │   ├── LoginAttemptService.java

# │   ├── LoginController.java

# │   ├── LoginFailureHandler.java

# │   ├── LoginSuccessHandler.java

# │   ├── MemberAdminController.java

# │   ├── MemberAdminException.java

# │   ├── MemberAdminService.java

# │   ├── PasswordResetController.java

# │   ├── PasswordResetMailer.java

# │   ├── PasswordResetService.java

# │   ├── RegistrationController.java

# │   ├── RegistrationService.java

# │   │

# │   └── dto/

# │

# ├── common/

# │   ├── domain/

# │   ├── security/

# │   └── validation/

# │

# └── admin/

# ```

# 

# \---

# 

# \## User Interface

# 

# User-related templates are located in:

# 

# ```text

# src/main/resources/templates/user/

# ```

# 

# Available pages include:

# 

# ```text

# user/

# ├── account.html

# ├── all-members.html

# ├── forgot-password.html

# ├── login.html

# ├── member-fragments.html

# ├── pending-members.html

# ├── register.html

# └── reset-password.html

# ```

# 

# \---

# 

# \## Security Components

# 

# The User \& Access Management module contains functionality for:

# 

# \- User authentication

# \- Role-based authorization

# \- User principal management

# \- Secure password hashing

# \- Login success handling

# \- Login failure handling

# \- Failed login tracking

# \- Password reset tokens

# \- Access-denied handling

# \- Audit logging

# 

# \---

# 

# \## Database Setup

# 

# The SQL scripts required by the project are available in the `database` directory.

# 

# The database schema should be created using:

# 

# ```text

# database/01\_schema.sql

# ```

# 

# Additional security-related database changes are available in:

# 

# ```text

# database/04\_security\_migration.sql

# ```

# 

# Database connection settings should be configured according to the application's environment.

# 

# > Do not commit database passwords or other sensitive credentials to the repository.

# 

# \---

# 

# \## Development

# 

# Development should be performed using feature branches.

# 

# The User \& Access Management module is currently developed on:

# 

# ```text

# user-access-management

# ```

# 

# Changes should be tested before being merged into the main branch.

# 

# \---

# 

# \## Project Status

# 

# The Library Management System is under active development.

# 

# Additional modules and documentation can be added to this README as the group project progresses.

