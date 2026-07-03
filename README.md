# Validation System - Desktop Edition 🏥

<div align="center">

**GxP-Compliant Temperature Validation Platform for Pharmaceutical Cold-Chain Storage**

[![Java 21](https://img.shields.io/badge/Java-21_LTS-ED8B00?style=flat&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.2-green?style=flat&logo=spring)](https://spring.io/projects/spring-boot)
[![JavaFX 21](https://img.shields.io/badge/JavaFX-21.0.2-blue?style=flat&logo=java)](https://openjfx.io/)
[![MySQL 8.0](https://img.shields.io/badge/MySQL-8.0-blue?style=flat&logo=mysql)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**Status:** Production-Ready | **Version:** 1.0.0-beta | **Build:** ✅ All Tests Passing  
**GxP Compliance:** ✅ FDA 21 CFR Part 11 | ✅ WHO Annex 11 | ✅ ISPE GAMP 5  
**Platform:** ⚠️ Windows 10/11 x64 only (FTDI USB driver requirement)

</div>

---

## 🎯 Overview

**Validation System** is a comprehensive pharmaceutical validation platform designed for qualifying cold-chain storage environments (refrigerators, freezers, warehouses) with temperature mapping and advanced statistical analysis.

### Why This Matters

Pharmaceutical companies must validate that storage environments maintain specified temperature ranges **continuously and reliably**. Manual validation is:
- ❌ Time-consuming (weeks of spreadsheet work)
- ❌ Error-prone (manual calculations)
- ❌ Non-compliant (lacks audit trail)
- ❌ Unmaintainable (no scientific rigor)

**Validation System** solves this with:
- ✅ **Automated data collection** from Testo temperature loggers
- ✅ **Scientific statistical analysis** (hypothesis testing, SPC, FFT)
- ✅ **Complete audit trail** (Hibernate Envers, every change tracked)
- ✅ **Professional PDF reports** (FDA-ready documentation)
- ✅ **GxP-compliant design** (built-in regulatory controls)

---

## ✨ Key Features

### 🌡️ Temperature Mapping
- **Multi-point temperature mapping** with spatial uniformity analysis
- **Testo 184 recorder integration** via USB (automated data import)
- **Real-time chamber monitoring** with threshold alerts
- **Geographical position tracking** (top, middle, bottom levels)

### 📊 Advanced Statistical Analysis Module
**Includes 4 complementary statistical engines:**

| Module | Purpose | Key Metrics |
|--------|---------|------------|
| **Descriptive Spatial Stats** | Characterize temperature distribution | Mean, σ, RSD (≤5%), skewness, kurtosis |
| **Hypothesis Testing** | Prove equivalence/differences | TOST, ANOVA, Kruskal-Wallis, Jarque-Bera |
| **SPC (Statistical Process Control)** | Assess equipment capability | Cp, Cpk (target ≥1.33 for GxP) |
| **Time-Series Analysis** | Detect cycles & anomalies | FFT spectrum, defrost cycle detection |

✅ **Performance:** Handles 1M+ data points in <105ms (JIT-optimized)  
✅ **Validated:** Benchmarked against NIST reference data  
✅ **Native Java:** No external math libraries → easier CSV (Computer System Validation)

### 🔐 Enterprise Security
- **Spring Security** with role-based access control
- **Hibernate Envers** - complete audit trail (who changed what, when)
- **Password policies** - enforced expiration, history, complexity
- **Inactivity monitoring** - automatic session timeout (15 min GxP requirement)
- **Access logging** - all operations tracked for compliance

### 📋 Regulatory Compliance
- **GxP-aware design** - every feature considers regulatory requirements
- **FDA 21 CFR Part 11** - electronic signature ready
- **WHO Technical Report 961** - cold-chain validation framework
- **ISPE GAMP 5** - software validation guidelines
- **Flyway migrations** - database versioning for reproducibility

### 📄 Professional Reporting
- **PDF report generation** with:
  - Statistical summaries
  - Control charts (Shewhart charts, SPC trends)
  - Hotspot/coldspot analysis
  - Defrost cycle documentation
  - Compliance checklist
- **Word document generation** (protocol-ready format)
- **CSV export** for further analysis

### 🛠️ Admin & Configuration
- **Multi-level organization** - Headquarters → Labs → Departments
- **Cooling device management** - track equipment and revalidation schedules
- **Calibration tracking** - sensor calibration certificates and expiration
- **Revalidation management** - schedule and execute periodic validation
- **Database backup** - MySQL dumps with retention policies (14 days)

---

## 🏗️ Architecture

```
validation-desktop/
├── src/main/java/com/mac/bry/desktop/
│   ├── config/              # Spring configuration
│   ├── controller/          # JavaFX controllers (UI layer)
│   │   └── helper/          # UI rendering helpers (Table, Cell factories)
│   ├── service/             # Business logic
│   │   ├── stats/           # Statistical analysis engines
│   │   │   ├── SensorStatsEngine.java          # Descriptive stats
│   │   │   ├── HypothesisTestingService.java   # TOST, ANOVA, F-test
│   │   │   ├── SpcEngine.java                  # Cp, Cpk calculation
│   │   │   ├── FftCalculator.java              # Fast Fourier Transform
│   │   │   ├── DefrostCycleDetector.java       # Defrost cycle detection
│   │   │   └── StatisticsAggregationService.java # Orchestration
│   │   ├── hotspot/         # Hotspot/coldspot analysis
│   │   └── *Service.java    # Domain services (30+ services)
│   ├── repository/          # JPA Data Access
│   ├── model/               # JPA entities
│   ├── dto/                 # Data Transfer Objects
│   └── security/            # Authentication & authorization
│   ├── src/main/resources/
│   │   ├── db/migration/        # Flyway migrations (V1 → V29)
│   │   ├── ui/                  # FXML templates
│   │   └── docs/                # 97 specification & validation documents
│   ├── src/test/java/
│   │   ├── integration/         # Integration tests (5+ test suites)
│   │   └── service/stats/       # Unit tests for statistical engines
│   └── pom.xml                  # Maven configuration (Java 21, Spring Boot 3.2.2)
```

### Design Patterns Applied
- **Facade Pattern** - TestoRevalidationFacade simplifies complex workflows
- **Service Layer** - Separation of business logic from controllers
- **Repository Pattern** - Data access abstraction via Spring Data JPA
- **DTO Pattern** - Clean data transfer between layers
- **Helper Pattern** - UI rendering logic extracted from controllers

### Key Technologies
| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Desktop UI** | JavaFX 21 | Modern, responsive desktop application |
| **UI Integration** | FxWeaver 2.0.1 | Spring Boot + JavaFX seamless integration |
| **Backend** | Spring Boot 3.2.2 | Enterprise framework, dependency injection |
| **ORM** | Hibernate + JPA | Object-relational mapping |
| **Database** | MySQL 8.0 | Persistent data storage |
| **Migrations** | Flyway 9.22.3 | Version-controlled schema management |
| **Audit Trail** | Hibernate Envers | Complete change tracking |
| **Security** | Spring Security 6.2.1 | Authentication & authorization |
| **Testing** | JUnit 5, AssertJ | Unit & integration testing |
| **Reporting** | Apache PDFBox | PDF generation |

---

## 📚 Documentation Structure

This project includes **97 detailed specification documents** covering all aspects of system design, validation, and operation.

### Document Organization

```
docs/
├── Core System
│   ├── SYSTEM_MODERNIZATION_2026.md           # Java 21 upgrade, architecture refactoring
│   ├── README.md                              # Docs overview & structure
│   └── refactor/                              # Refactoring documentation
│       ├── REFACTORING_SUMMARY.md             # Code refactoring achievements
│       ├── GOD_CLASSES_ANALYSIS.md            # Complexity analysis
│       └── REFACTORING_*.md                   # Phase-by-phase details
│
├── Statistical Analysis Module 📊
│   ├── STATS_DESCRIPTIVE_SPATIAL_BA.md        # Business analysis
│   ├── STATS_DESCRIPTIVE_SPATIAL_IMPLEMENTATION_PLAN.md
│   ├── STATS_DESCRIPTIVE_SPATIAL_TEST_SCENARIOS.md
│   ├── STATS_HYPOTHESIS_TESTING_BA.md
│   ├── STATS_HYPOTHESIS_TESTING_IMPLEMENTATION_PLAN.md
│   ├── STATS_SPC_TRENDS_BA.md                 # SPC requirements
│   ├── STATS_SPC_TRENDS_IMPLEMENTATION_PLAN.md
│   ├── STATS_TIME_SERIES_CYCLES_BA.md         # Defrost detection
│   ├── STATS_ORCHESTRATION_BA.md              # Integration layer
│   ├── STATS_PERFORMANCE_AUDIT_REPORT.md      # ✅ Validation report
│   └── STATS_PARAMETERS_TABLES.md             # Reference values
│
├── Features & Modules
│   ├── COOLING_DEVICE_BA.md                   # Equipment management
│   ├── THERMO_RECORDER_BA.md                  # Temperature logger integration
│   ├── TESTO_184_BA.md                        # Testo 184 specific features
│   ├── CHAMBER_MAPPING_BA.md                  # Spatial measurement points
│   ├── HOTSPOT_DETECTION_README.md            # Thermal analysis
│   ├── USER_MANAGEMENT_BA.md                  # Access control
│   ├── USER_AUDIT_BA.md                       # Activity tracking
│   ├── DATABASE_BACKUP_BA.md                  # Data protection
│   ├── ADMIN_PANEL_BA.md                      # Administration features
│   ├── ORGANIZATION_BA.md                     # Multi-tenant structure
│   ├── BA_PROPAGATION_AWARE_EXCURSION_CLASSIFIER.md   # Heat propagation vector excursion classifier (BA)
│   ├── IMPL_PROPAGATION_AWARE_EXCURSION_CLASSIFIER.md # Technical implementation details
│   ├── TEST_PROPAGATION_AWARE_EXCURSION_CLASSIFIER.md # Verification and validation plan
│   └── REVALIDATION_*.md                      # Periodic validation
│
├── Testo Device Integration
│   ├── TESTO_184_BA.md
│   ├── TESTO_USB_BA.md
│   ├── TESTO_USB_PROGRAMMING_BA.md
│   ├── TESTO_USB_PROGRAMMING_TECHNICAL_SPEC.md
│   ├── TESTO_COMPARATIVE_QUALIFICATION_PLAN.md
│   ├── ANALIZA_TESTO_174T_FINAL.md
│   └── TESTO_USB_ANALYSIS.md
│
├── Compliance & Security
│   ├── P12_STORAGE_GXP_RODO_ANALYSIS.md       # GDPR/GxP analysis
│   ├── P12_STORAGE_GXP_GDPR_ANALYSIS_EN.md
│   ├── SECURITY_MODULE_EVALUATION.md          # Security assessment
│   ├── METROLOGICAL_STATS_BA_TECH_SPEC.md    # Measurement standards
│   └── REVALIDATION_PROCEDURE_BA_TECH_SPEC.md
│
├── Transport Validation (4 scenarios)
│   ├── TRANSPORT_UNIT1_BA.md
│   ├── TRANSPORT_UNIT2_BA.md
│   ├── TRANSPORT_UNIT3_BA.md
│   └── TRANSPORT_UNIT4_BA.md
│       (Each with Technical Spec + Test Scenarios)
│
├── Research & Articles
│   ├── article_hotspot_coldspot_PL.md         # Hotspot analysis article
│   ├── article_hotspot_coldspot_EN.md
│   ├── article_java_implementation_PL.md      # Java implementation guide
│   ├── article_java_implementation_EN.md
│   ├── article_defrost_cycle_detection_PL.md   # Defrost cycle detection article (PL)
│   ├── article_defrost_cycle_detection_EN.md   # Defrost cycle detection article (EN)
│   ├── article_propagation_vector_part1_PL.md # Heat propagation part 1 (PL)
│   ├── article_propagation_vector_part1_EN.md # Heat propagation part 1 (EN)
│   ├── article_propagation_vector_part2_PL.md # Heat propagation part 2 (PL)
│   ├── article_propagation_vector_part2_EN.md # Heat propagation part 2 (EN)
│   └── sources_for_hotspot_article.md
│
└── Test Scenarios
    ├── STATS_*_TEST_SCENARIOS.md              # Statistical module tests
    ├── COOLING_DEVICE_TEST_SCENARIOS.md       # Equipment validation
    ├── TESTO_184_TEST_SCENARIOS.md            # Device-specific tests
    ├── USER_MANAGEMENT_TEST_SCENARIOS.md      # Access control tests
    ├── USER_AUDIT_TEST_SCENARIOS.md           # Audit verification
    ├── TRANSPORT_UNIT*_TEST_SCENARIOS.md      # Transport validation
    └── REVALIDATION_APPENDIX3_TEST_SCENARIOS.md
```

### How to Use Documentation

**For Understanding Features:**
1. Start with `[FEATURE]_BA.md` - "what" and "why"
2. Read `[FEATURE]_TECHNICAL_SPEC.md` - implementation details
3. Check `[FEATURE]_TEST_SCENARIOS.md` - how to verify functionality

**For Regulatory Compliance:**
- Read: `P12_STORAGE_GXP_*.md` (GDPR/GxP alignment)
- Reference: `REVALIDATION_PROCEDURE_BA_TECH_SPEC.md` (FDA validation)
- Check: `STATS_PERFORMANCE_AUDIT_REPORT.md` (evidence of validation)

**For System Architecture:**
- Start: `SYSTEM_MODERNIZATION_2026.md` (technical overview)
- Details: `refactor/REFACTORING_SUMMARY.md` (code improvements)
- Patterns: `refactor/REFACTORING_PLAN_*.md` (design decisions)

**For Statistical Module:**
- Overview: `STATS_ORCHESTRATION_BA.md` (unified approach)
- Performance: `STATS_PERFORMANCE_AUDIT_REPORT.md` ✅ (validated)
- Algorithms: Individual `STATS_*_IMPLEMENTATION_PLAN.md` files

---

## 🚀 Quick Start

### Prerequisites
- **Java 21 LTS** ([OpenJDK Temurin](https://adoptium.net/))
- **MySQL 8.0** or later
- **Maven 3.8+**
- **Git**
- **Python 3.9+** (required for Testo device communication)
  - Verify: `python --version`
  - Optional charts: `pip install matplotlib`
  - ⚠️ On Windows, `python` may open Microsoft Store instead of the interpreter. If so, reinstall Python from [python.org](https://www.python.org/) with "Add to PATH" checked.
- **FTDI D2XX driver** (required for Testo 174T USB — not needed for Testo 184)
  - Install [Testo Comfort Software Basic](https://www.testo.com/en/service-support/software) (driver included), or
  - Download `ftd2xx64.dll` from [ftdichip.com](https://ftdichip.com/drivers/d2xx-drivers/) and place in `C:\Windows\System32\`
  - Driver search paths can be customized in `src/main/resources/testo/testo_config.yml`

### 🔍 Automated Prerequisites Check (Recommended)

Before manual setup, run the **`check_requirements.ps1`** script to verify all dependencies and optionally install missing ones:

```powershell
# Report-only mode (no changes, just verification)
powershell -ExecutionPolicy Bypass -File ".\check_requirements.ps1" -ReportOnly

# Interactive mode (asks before each installation)
powershell -ExecutionPolicy Bypass -File ".\check_requirements.ps1"

# Auto-install all missing dependencies (requires Administrator)
powershell -ExecutionPolicy Bypass -File ".\check_requirements.ps1" -AutoInstall

# Skip optional checks (matplotlib, etc.)
powershell -ExecutionPolicy Bypass -File ".\check_requirements.ps1" -ReportOnly -SkipOptional
```

**What the script checks:**

| Check | Details |
|-------|---------|
| **System** | Windows x64, RAM, disk space, admin rights, winget availability |
| **Java 21** | JDK presence, version, JAVA_HOME, Temurin distribution, `javac` cross-check |
| **Maven 3.8+** | Installation, version, Java compatibility |
| **MySQL 8.0+** | Client, service status, binary paths, `mysqldump`, database existence |
| **Git** | Installation and version |
| **Python 3.9+** | Version, pip, matplotlib (optional), Microsoft Store stub detection |
| **FTDI D2XX** | Driver DLL presence (required only for Testo 174T) |
| **Project config** | `.env` file, Word report templates |

**Script flags:**

| Flag | Behavior |
|------|----------|
| *(no flags)* | Interactive — asks before each installation |
| `-ReportOnly` | Only shows results, no installation prompts |
| `-AutoInstall` | Installs all missing dependencies via `winget` without asking |
| `-SkipOptional` | Skips optional checks (matplotlib) |

**Exit codes:** `0` = all requirements met, `1` = missing requirements (CI/CD compatible).

> ⚠️ **Note:** After installing dependencies via the script, you may need to restart your terminal for PATH changes to take effect.

### 1. Clone Repository
```bash
git clone https://github.com/MacBry/validation-desktop-system.git
cd validation-desktop-system
```

### 2. Configure Environment
Create `.env` file in project root:
```bash
cp .env.example .env
```

Edit `.env` with your settings:
```bash
# Database
DB_HOST=localhost
DB_PORT=3306
DB_NAME=validation_desktop_db
DB_USERNAME=root
DB_PASSWORD=your_secure_password

# SMTP (for email notifications)
SMTP_HOST=smtp-relay.brevo.com
SMTP_PORT=587
SMTP_USERNAME=your_email@example.com
SMTP_PASSWORD=your_smtp_password

# Backup path (Windows example)
MYSQL_DUMP_PATH=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe
```

### 3. Provide Word Report Templates

Word report templates are **not included in this repository** (internal company documents). Before building, place the following files in `src/main/resources/templates/`:

```
src/main/resources/templates/
├── appendix_3_template.docx   # Revalidation protocol
├── appendix_7_template.docx   # Temperature mapping
└── appendix_8_template.docx   # Measurement summary
```

> Without these files the application starts normally, but generating Word reports will throw a `FileNotFoundException`.

### 4. Build Application
```bash
mvn clean install
```

**Expected Output:**
```
[INFO] BUILD SUCCESS
[INFO] Total time: 2.5 min
[INFO] Tests run: 85, Failures: 0
```

### 5. Run Application
```bash
mvn spring-boot:run
```

The application launches with:
- ✅ JavaFX desktop window opens
- ✅ Spring Boot backend initializes
- ✅ MySQL database migrations run automatically (Flyway — 29 versions)
- ✅ Login screen appears

### 6. Login with Default Credentials
```
Username: admin
Password: admin
```

⚠️ **IMPORTANT:** Change admin password immediately (system enforces after first login)

---

## 📖 Module Quick Reference

### Statistical Analysis (`service/stats/`)
**Purpose:** Scientifically validate temperature stability

| Class | Calculates | Performance | GxP Validated |
|-------|-----------|-------------|---------------|
| `SensorStatsEngine` | Mean, σ, RSD, skewness, kurtosis | Native Java | ✅ Yes |
| `HypothesisTestingService` | TOST, ANOVA, F-test, Jarque-Bera | <50ms | ✅ Yes |
| `SpcEngine` | Cp, Cpk capability indexes | <1ms | ✅ Yes |
| `FftCalculator` | FFT spectrum (Cooley-Tukey) | 104ms for 1M points | ✅ Yes |
| `DefrostCycleDetector` | Defrost cycle detection & timing | 29ms for 10k points | ✅ Yes |
| `StatisticsAggregationService` | Orchestrates all above | Single-point entry | ✅ Yes |

**Test Coverage:** 95% (6 test classes, 100+ test cases)  
**Validation Report:** `docs/STATS_PERFORMANCE_AUDIT_REPORT.md`

### Security (`security/`)
- **Authentication:** Spring Security + database-backed users
- **Authorization:** Role-based (SUPER_ADMIN, DEPT_ADMIN, USER)
- **Audit Trail:** Hibernate Envers (every change tracked)
- **Password Policy:** Enforced expiration, complexity, history validation

### Device Management (`service/CoolingDeviceService`)
- Create/edit cooling equipment
- Track calibration expiration
- Schedule revalidations
- Monitor validation status (Valid, Revalidation Required, Invalid)

### Data Import (`service/TestoUsbImportService`)
- USB connection detection for Testo loggers
- Automated data extraction
- Conflict resolution (duplicate handling)
- Real-time validation import to database

### Reporting (`service/TestoPdfReportService`, `TestoRevalidationWordService`)
- Professional PDF generation with charts
- Word document protocols (ready for auditors)
- Includes statistical summaries
- GxP compliance checklist

---

## 🧪 Testing

### Run All Tests
```bash
mvn test
```

### Run Specific Test Suite
```bash
# Statistics module tests
mvn test -Dtest=SensorStatsEngineTest
mvn test -Dtest=HypothesisTestingServiceTest
mvn test -Dtest=FftCalculatorTest

# Integration tests
mvn test -Dtest=*IntegrationTest
```

### Test Database
Tests use H2 in-memory database (no MySQL required):
```properties
# application-test.yml
spring.datasource.url=jdbc:h2:mem:testdb
spring.h2.console.enabled=true
```

### Coverage Report
```bash
mvn jacoco:report
# Report at: target/site/jacoco/index.html
```

---

## 📊 Project Statistics

- **Lines of Code:** ~15,000 (main code)
- **Test Classes:** 7+ with 100+ test cases
- **Documentation:** 97 specification files
- **Database Migrations:** 29 Flyway versions
- **Service Classes:** 30+
- **Controller Classes:** 15+
- **Test Coverage:** 85%+ (stats module)
- **Build Time:** ~2.5 minutes
- **Performance:** Handles 1M+ temperature points in <105ms

---

## 🔗 Key Documentation Links

### For Developers
- **Getting Started:** [docs/README.md](docs/README.md)
- **System Architecture:** [docs/SYSTEM_MODERNIZATION_2026.md](docs/SYSTEM_MODERNIZATION_2026.md)
- **Code Refactoring:** [docs/refactor/REFACTORING_SUMMARY.md](docs/refactor/REFACTORING_SUMMARY.md)

### For QA/Validators
- **Statistical Module:** [docs/STATS_ORCHESTRATION_BA.md](docs/STATS_ORCHESTRATION_BA.md)
- **Performance Report:** [docs/STATS_PERFORMANCE_AUDIT_REPORT.md](docs/STATS_PERFORMANCE_AUDIT_REPORT.md)
- **Test Scenarios:** All `*_TEST_SCENARIOS.md` files

### For Compliance Officers
- **GxP Compliance:** [docs/P12_STORAGE_GXP_RODO_ANALYSIS.md](docs/P12_STORAGE_GXP_RODO_ANALYSIS.md)
- **Security Evaluation:** [docs/SECURITY_MODULE_EVALUATION.md](docs/SECURITY_MODULE_EVALUATION.md)
- **Revalidation Procedure:** [docs/REVALIDATION_PROCEDURE_BA_TECH_SPEC.md](docs/REVALIDATION_PROCEDURE_BA_TECH_SPEC.md)

### For Product Managers
- **Feature List:** See each `[FEATURE]_BA.md` file
- **Implementation Status:** Commit history and 11+ commits with features
- **Roadmap:** See open issues and project milestones

---

## 🛠️ Development Workflow

### Prerequisites Setup
```bash
# Switch to Java 21 (Windows PowerShell)
./Switch-Java21.ps1

# Or manually:
$env:JAVA_HOME = "C:\Users\[YourUsername]\scoop\apps\openjdk21\current"
```

### IDE Configuration (IntelliJ IDEA)
1. **File → Project Structure → SDKs** → Add JDK 21
2. **Run → Edit Configurations** → Add Spring Boot configuration:
   - Main class: `com.mac.bry.desktop.ValidationDesktopApplication`
   - VM options: `-Djavafx.userAgentStylesheetUrl=file:///path/to/resources`
3. **Enable Annotation Processing** (for Lombok)

### Build & Run
```bash
# Development with hot reload (Maven)
mvn spring-boot:run

# Or IDE Run button (faster feedback)
```

### Create Database (Manual Setup)
```sql
CREATE DATABASE validation_desktop_db 
  CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;

CREATE USER 'validation_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON validation_desktop_db.* 
  TO 'validation_user'@'localhost';
FLUSH PRIVILEGES;
```

Flyway will automatically run migrations on startup.

---

## 🤝 Contributing

### Code Style
- **Java version:** Java 21
- **Formatter:** Google Java Format
- **Checker:** SpotBugs (static analysis)
- **Style:** Follow existing patterns (Service/Repository/DTO)

### Before Committing
```bash
# Format code
mvn com.spotify.fmt:fmt-maven-plugin:format

# Run tests
mvn test

# Check for bugs
mvn spotbugs:check

# Verify no secrets committed
git status --porcelain | grep "\.env"
```

### Commit Message Format
```
type(module): brief description

Longer explanation of the change and why it was made.
References: GitHub issue or Jira ticket (optional)

Co-Authored-By: [Your Name] <your.email@example.com>
```

**Types:** `feat`, `fix`, `refactor`, `test`, `docs`, `chore`  
**Modules:** `stats`, `security`, `ui`, `db`, `api`, `device`

### Pull Request Process
1. Fork the repository
2. Create feature branch: `git checkout -b feature/your-feature`
3. Make changes and commit with proper messages
4. Ensure all tests pass: `mvn verify`
5. Push and create PR with description
6. Wait for code review and CI/CD checks

---

## 📋 Regulatory Compliance

### FDA 21 CFR Part 11 (Electronic Records)
- ✅ Electronic signatures (Spring Security)
- ✅ Audit trail (Hibernate Envers)
- ✅ Access control (role-based)
- ✅ Data integrity (database constraints)
- ✅ System validation (documented procedures)

### WHO Technical Report Series 961 (Cold Chain)
- ✅ Temperature mapping methodology
- ✅ Spatial uniformity analysis
- ✅ Statistical qualification
- ✅ Periodic revalidation

### ISPE GAMP 5 (Software Validation)
- ✅ Requirements documentation (97 BA/IMP docs)
- ✅ Design specifications (technical specs)
- ✅ Implementation verification (test scenarios)
- ✅ System validation (performance audit report)

---

## 🐛 Troubleshooting

### MySQL Connection Error
```
Error: Communications link failure
```
**Solution:**
1. Verify MySQL is running: `mysql --version`
2. Check credentials in `.env`
3. Ensure database exists: `mysql -u root -p -e "SHOW DATABASES;"`

### Flyway Migration Error
```
Error: Validate failed. Failed migrations
```
**Solution:**
1. Check `docs/db/migration/` folder exists
2. Verify migration file permissions
3. Clean and rebuild: `mvn clean install`

### JavaFX Display Issues
```
Error: javafx-weaver not found
```
**Solution:**
```bash
mvn clean
mvn install -DskipTests
# Ensure FxWeaver dependency is in pom.xml (should be)
```

### Python / Testo Device Error
```
Nie znaleziono skryptu testo_usb_programmer.py
```
**Solution:**
1. Verify Python is installed: `python --version`
2. If using Testo 174T: ensure FTDI D2XX driver is installed (`ftd2xx64.dll`)
3. Check driver paths in `src/main/resources/testo/testo_config.yml`
4. For Testo 184: verify the device appears as a USB drive (mass storage)

### Word Report Error
```
FileNotFoundException: /templates/appendix_3_template.docx
```
**Solution:** Place company Word templates in `src/main/resources/templates/` (see [Step 3](#3-provide-word-report-templates))

### JIT Warmup Warning
Statistical tests may run slower on first execution (JIT compilation phase):
- **Expected:** Warmup phase takes 1-2 seconds
- **Normal:** Subsequent calls execute in <50ms
- **Info:** This is expected behavior and improves with repeated use

---

## 📊 Performance Benchmarks

### Statistical Module Performance
| Operation | Input Size | Time | Status |
|-----------|-----------|------|--------|
| Defrost Cycle Detection | 10,000 points | 29 ms | ✅ PASS (target: <100ms) |
| FFT Spectrum | 1,048,576 points | 104 ms | ✅ PASS (target: <500ms) |
| ANOVA (4 groups) | 1,000 points | <1 ms | ✅ PASS |
| Hypothesis Testing | 100 points | <1 ms | ✅ PASS |

**Full Benchmark Report:** [docs/STATS_PERFORMANCE_AUDIT_REPORT.md](docs/STATS_PERFORMANCE_AUDIT_REPORT.md)

---

## 📞 Support & Contact

### Issue Tracking
- **Bug Reports:** GitHub Issues (with reproduction steps)
- **Feature Requests:** GitHub Discussions
- **Security Issues:** Please email privately (do not use public issues)

### Documentation
- **Questions about features:** Check `/docs` folder first
- **Implementation questions:** See relevant `*_IMPLEMENTATION_PLAN.md`
- **Test guidance:** Review `*_TEST_SCENARIOS.md`

### Community
- **Questions:** Create GitHub Discussion
- **Ideas:** Open Feature Request issue
- **Contributions:** Follow Contributing guidelines above

---

## 📜 License

This project is licensed under the **MIT License** - see [LICENSE](LICENSE) file for details.

### License Highlights
- ✅ Free for commercial use
- ✅ Modify and distribute
- ✅ Use in closed-source projects
- ✅ Required: Include copyright notice

---

## 🏆 Acknowledgments

### Technologies Used
- **Spring Boot** - Enterprise framework
- **Hibernate** - ORM framework
- **JavaFX** - Desktop UI toolkit
- **MySQL** - Relational database
- **Apache Commons** - Math utilities

### Validation References
- WHO Technical Report Series 961 - Guidelines on temperature-sensitive pharmaceutical products
- ISPE GAMP 5 - Guidance to industry on compliant pharmaceutical computer systems
- FDA 21 CFR Part 11 - Electronic Records; Electronic Signatures

### Domain Expertise
- GxP (Good Manufacturing Practice) compliance
- Pharmaceutical cold-chain validation
- Temperature mapping standards
- Statistical process control (SPC)
- System validation methodologies

---

## ⭐ Show Your Support

If this project helped you understand pharmaceutical validation systems or GxP-compliant software design:
- ⭐ Star this repository
- 🔗 Share with your team
- 💬 Provide feedback

---

**Version:** 1.0.0-beta | **Last Updated:** 2026-06-07  
**Repository:** [github.com/MacBry/validation-desktop-system](https://github.com/MacBry/validation-desktop-system)  
**Issues:** [github.com/MacBry/validation-desktop-system/issues](https://github.com/MacBry/validation-desktop-system/issues)
