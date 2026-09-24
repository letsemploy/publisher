<!-- Part of the specification: sections are numbered across all files, and code cites them as "spec N.M". -->
[Specification index](../SPEC.md) · ← [5. Feed publishing](05-feed-publishing.md) · [7. User interface](07-user-interface.md) →

# 6. The ojobpub document contract

This section defines the mapping from the domain model onto the oJobPub v1 schema. The schema is closed
(`additionalProperties: false` at the root, on `employer` and on each job), so **no field outside this
mapping may be emitted**, and a null value **must** be omitted rather than serialized as `null`.

## 6.1 Root object

| JSON key | Required | Source | Format |
|---|---|---|---|
| `version` | **yes** | constant | exactly `"1.0"` |
| `lastUpdated` | **yes** | §5.4 | RFC 3339 date-time **with offset**, UTC, e.g. `2026-09-21T08:15:00Z` |
| `employer` | **yes** | the feed's employer | §6.2 |
| `jobs` | **yes** | §5.2 | array, may be empty |

`lastUpdated` **must** be serialized from an instant with an explicit offset. A local date-time
(`2026-09-21T08:15:00`, no offset) is not a valid RFC 3339 `date-time` and must never be emitted.

## 6.2 `employer`

| JSON key | Required | Source | Format |
|---|---|---|---|
| `name` | **yes** | `Employer.name` | 1..255 characters |
| `location` | **yes** | `Employer.headquarters` | §6.3 |
| `industry` | no | `Employer.industry` | omitted when blank |
| `url` | no | `Employer.url` | absolute URI, omitted when blank |

## 6.3 `location`

| JSON key | Source | Format |
|---|---|---|
| `city` | `Location.city` | as stored |
| `country` | `Location.country` | ISO 3166-1 alpha-2, **uppercase**, e.g. `CH` |

Country codes **must** be uppercase everywhere in the document — in the employer's location and in every
job location alike. Case must not differ between the two.

The schema itself only constrains `country` to two characters, so lowercase would technically validate.
Uppercase is nevertheless mandatory here: ISO 3166-1 alpha-2 is defined in uppercase, the schema
documents it as `US, DE, CH`, and consumers that compare codes as plain strings will silently fail to
match a lowercase value. Emitting the same field in different cases in different places is worse still.

## 6.4 `job`

| JSON key | Required | Source | Format |
|---|---|---|---|
| `title` | **yes** | `Job.title` | ≤255 |
| `url` | **yes** | `Job.url` | absolute URI |
| `language` | **yes** | `Job.languageCode` | ISO 639-1, **lowercase**, exactly 2 characters |
| `publishedAt` | **yes** | `Job.publishedAt` | `yyyy-MM-dd` |
| `jobType` | **yes** | `Job.jobType` | §6.5 |
| `locations` | **yes** | `Job.locations` | array, **at least one** entry, §6.3 |
| `description` | no | `Job.description` | ≤1000 |
| `category` | no | `Job.category` | ≤255 |
| `referenceId` | no | `Job.referenceId` | ≤255 |
| `workType` | no | `Job.workType` | §6.5 |
| `experienceLevel` | no | `Job.experienceLevel` | §6.5 |
| `startDate` | no | `Job.startDate` | `yyyy-MM-dd` |
| `endDate` | no | `Job.endDate` | `yyyy-MM-dd` |
| `applyBefore` | no | `Job.applyBefore` | `yyyy-MM-dd` |
| `workLoad` | no | §6.6 | omitted entirely when both percentages are absent |
| `salary` | no | §6.7 | omitted entirely when both amounts are absent |
| `tags` | no | `Job.tags` | array of tag names, **lowercase**, each ≤28 characters, at most 16, unique, sorted ascending |

`tags` **must** be populated from the job's tags. It is part of the contract and consumers use it for
matching; emitting the field as permanently absent wastes the most useful signal in the document.

## 6.5 Enum mapping

Enum values **must** be produced through an explicit mapping table. Deriving them from the enum
constant's name (`name().toLowerCase()` or similar) is forbidden: it produces `on_site` where the schema
requires `on-site`, and it silently breaks the contract whenever a constant is renamed.

**`jobType`**

| Domain | JSON |
|---|---|
| `PERMANENT` | `permanent` |
| `CONTRACT` | `contract` |
| `TEMPORARY` | `temporary` |
| `FREELANCE` | `freelance` |
| `VOLUNTEER` | `volunteer` |
| `APPRENTICESHIP` | `apprenticeship` |
| `INTERNSHIP` | `internship` |

**`workType`**

| Domain | JSON |
|---|---|
| `ON_SITE` | **`on-site`** |
| `REMOTE` | `remote` |
| `HYBRID` | `hybrid` |

**`experienceLevel`**

| Domain | JSON |
|---|---|
| `JUNIOR` | `junior` |
| `MID` | `mid` |
| `SENIOR` | `senior` |
| `LEAD` | `lead` |
| `MANAGER` | `manager` |
| `DIRECTOR` | `director` |
| `EXECUTIVE` | `executive` |

**`salary.interval`**

| Domain | JSON |
|---|---|
| `HOURLY` | `hourly` |
| `DAILY` | `daily` |
| `WEEKLY` | `weekly` |
| `MONTHLY` | `monthly` |
| `YEARLY` | `yearly` |

## 6.6 `workLoad`

Emitted when at least one percentage is present. Each key is emitted only if its own value is present —
a missing maximum must not be fabricated from the minimum.

| JSON key | Source | Format |
|---|---|---|
| `minPercentage` | `Job.workLoadPercentMin` | number 0..100 |
| `maxPercentage` | `Job.workLoadPercentMax` | number 0..100 |

## 6.7 `salary`

Emitted when at least one amount is present. Each key is emitted only if its own value is present.
Because §3.3 makes currency and interval mandatory whenever an amount exists, a published salary block
always carries both.

| JSON key | Source | Format |
|---|---|---|
| `min` | `Job.salaryMin` | number ≥ 0 |
| `max` | `Job.salaryMax` | number ≥ 0 |
| `currency` | `Job.salaryCurrency` | ISO 4217, **uppercase**, 3 characters |
| `interval` | `Job.salaryInterval` | §6.5 |

Amounts **must** be serialized as JSON numbers without loss of the stored precision. Narrowing a decimal
amount to a 32-bit float is not acceptable — six-figure salaries lose accuracy.

## 6.8 Worked example

```json
{
  "version": "1.0",
  "lastUpdated": "2026-09-21T08:15:00Z",
  "employer": {
    "name": "Acme AG",
    "url": "https://www.acme.example",
    "industry": "Software",
    "location": { "city": "Bern", "country": "CH" }
  },
  "jobs": [
    {
      "title": "Senior Backend Engineer",
      "description": "Design and operate our job distribution platform.",
      "category": "Engineering",
      "referenceId": "ACME-2026-014",
      "jobType": "permanent",
      "workType": "on-site",
      "experienceLevel": "senior",
      "workLoad": { "minPercentage": 80, "maxPercentage": 100 },
      "salary": { "min": 110000, "max": 135000, "currency": "CHF", "interval": "yearly" },
      "locations": [
        { "city": "Bern", "country": "CH" },
        { "city": "Zürich", "country": "CH" }
      ],
      "publishedAt": "2026-09-01",
      "startDate": "2026-11-01",
      "applyBefore": "2026-10-15",
      "language": "en",
      "url": "https://www.acme.example/jobs/ACME-2026-014",
      "tags": ["java", "kubernetes", "spring"]
    },
    {
      "title": "Praktikum Produktdesign",
      "jobType": "internship",
      "workType": "hybrid",
      "locations": [{ "city": "Bern", "country": "CH" }],
      "publishedAt": "2026-09-18",
      "language": "de",
      "url": "https://www.acme.example/jobs/ACME-2026-021"
    }
  ]
}
```

The second job shows the minimum conforming posting: only the six required job fields, everything else
omitted rather than nulled.

## 6.9 Versioning

The URL carries the contract version (`/ojobpub/v1/...`) and the document repeats it in `version`.

- Additive, backwards-compatible changes within v1 are permitted.
- Any change that removes a field, narrows an enum, or changes a value's meaning requires a new
  versioned path (`/ojobpub/v2/...`) served **alongside** v1.
- When the upstream schema publishes a new version, the existing version must keep being served until
  consumers have migrated. Feed URLs are infrastructure for other people's systems.
