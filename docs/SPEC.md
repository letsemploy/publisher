# ojobpub-publisher — Product & Technical Specification

**Status:** target state. This specification describes how the application is meant to look and
behave. Where it disagrees with the current implementation, the specification wins.

**Normative language:** *must* = required for conformance; *should* = strongly recommended, deviation
needs a reason; *may* = optional.

**How this specification is organised.** It is split into one file per chapter under
[`docs/spec/`](spec/). Sections keep one numbering across all of them, and code comments cite that
number — `spec 4.3` is §4.3 in [4. Job lifecycle](spec/04-job-lifecycle.md). A cross-reference in
the text such as §2.6 means the same thing: find the chapter below, then the section.

---

## Contents

- **[1. Purpose and scope](spec/01-purpose.md)**
  - [1.1 What this is](spec/01-purpose.md#11-what-this-is)
  - [1.2 What this is not](spec/01-purpose.md#12-what-this-is-not)
  - [1.3 Consumers](spec/01-purpose.md#13-consumers)
  - [1.4 The published contract](spec/01-purpose.md#14-the-published-contract)
- **[2. Actors and access](spec/02-actors-and-access.md)**
  - [2.1 Roles](spec/02-actors-and-access.md#21-roles)
  - [2.2 Authentication](spec/02-actors-and-access.md#22-authentication)
  - [2.3 Development mode](spec/02-actors-and-access.md#23-development-mode)
  - [2.4 Public vs authenticated surface](spec/02-actors-and-access.md#24-public-vs-authenticated-surface)
  - [2.5 Employer context](spec/02-actors-and-access.md#25-employer-context)
  - [2.6 Invitations](spec/02-actors-and-access.md#26-invitations)
  - [2.7 Ownership and role changes](spec/02-actors-and-access.md#27-ownership-and-role-changes)
  - [2.8 API authentication](spec/02-actors-and-access.md#28-api-authentication)
- **[3. Domain model](spec/03-domain-model.md)**
  - [3.1 Employer](spec/03-domain-model.md#31-employer)
  - [3.2 Location](spec/03-domain-model.md#32-location)
  - [3.3 Job](spec/03-domain-model.md#33-job)
  - [3.4 Tag](spec/03-domain-model.md#34-tag)
  - [3.5 Feed](spec/03-domain-model.md#35-feed)
  - [3.6 Slugs](spec/03-domain-model.md#36-slugs)
  - [3.7 User](spec/03-domain-model.md#37-user)
  - [3.8 Invitation](spec/03-domain-model.md#38-invitation)
  - [3.9 Membership](spec/03-domain-model.md#39-membership)
  - [3.10 ServiceToken](spec/03-domain-model.md#310-servicetoken)
  - [3.11 Actor](spec/03-domain-model.md#311-actor)
- **[4. Job lifecycle](spec/04-job-lifecycle.md)**
  - [4.1 States](spec/04-job-lifecycle.md#41-states)
  - [4.2 `publishedAt`](spec/04-job-lifecycle.md#42-publishedat)
  - [4.3 Publication requirements](spec/04-job-lifecycle.md#43-publication-requirements)
  - [4.4 The date window](spec/04-job-lifecycle.md#44-the-date-window)
- **[5. Feed publishing](spec/05-feed-publishing.md)**
  - [5.1 URL](spec/05-feed-publishing.md#51-url)
  - [5.2 Inclusion predicate](spec/05-feed-publishing.md#52-inclusion-predicate)
  - [5.3 Defensive serving](spec/05-feed-publishing.md#53-defensive-serving)
  - [5.4 Caching and freshness](spec/05-feed-publishing.md#54-caching-and-freshness)
- **[6. The ojobpub document contract](spec/06-document-contract.md)**
  - [6.1 Root object](spec/06-document-contract.md#61-root-object)
  - [6.2 `employer`](spec/06-document-contract.md#62-employer)
  - [6.3 `location`](spec/06-document-contract.md#63-location)
  - [6.4 `job`](spec/06-document-contract.md#64-job)
  - [6.5 Enum mapping](spec/06-document-contract.md#65-enum-mapping)
  - [6.6 `workLoad`](spec/06-document-contract.md#66-workload)
  - [6.7 `salary`](spec/06-document-contract.md#67-salary)
  - [6.8 Worked example](spec/06-document-contract.md#68-worked-example)
  - [6.9 Versioning](spec/06-document-contract.md#69-versioning)
- **[7. User interface](spec/07-user-interface.md)**
  - [7.1 Principles](spec/07-user-interface.md#71-principles)
  - [7.2 JavaScript budget](spec/07-user-interface.md#72-javascript-budget)
  - [7.3 Layout](spec/07-user-interface.md#73-layout)
  - [7.4 htmx interaction patterns](spec/07-user-interface.md#74-htmx-interaction-patterns)
  - [7.5 Component vocabulary](spec/07-user-interface.md#75-component-vocabulary)
  - [7.6 Lists](spec/07-user-interface.md#76-lists)
  - [7.7 Forms](spec/07-user-interface.md#77-forms)
  - [7.8 Pickers without a JavaScript library](spec/07-user-interface.md#78-pickers-without-a-javascript-library)
  - [7.9 Accessibility](spec/07-user-interface.md#79-accessibility)
  - [7.10 Dashboard](spec/07-user-interface.md#710-dashboard)
  - [7.11 Jobs](spec/07-user-interface.md#711-jobs)
  - [7.12 Feeds](spec/07-user-interface.md#712-feeds)
  - [7.13 Employers](spec/07-user-interface.md#713-employers)
  - [7.14 Locations](spec/07-user-interface.md#714-locations)
  - [7.15 Tags](spec/07-user-interface.md#715-tags)
  - [7.16 Invitations](spec/07-user-interface.md#716-invitations)
  - [7.17 API tokens](spec/07-user-interface.md#717-api-tokens)
  - [7.18 People](spec/07-user-interface.md#718-people)
  - [7.19 Sign-in](spec/07-user-interface.md#719-sign-in)
- **[8. Cross-cutting behaviour](spec/08-cross-cutting.md)**
  - [8.1 Internationalization](spec/08-cross-cutting.md#81-internationalization)
  - [8.2 Validation and errors](spec/08-cross-cutting.md#82-validation-and-errors)
  - [8.3 Lists](spec/08-cross-cutting.md#83-lists)
  - [8.4 Resource limits](spec/08-cross-cutting.md#84-resource-limits)
- **[9. Technical shape](spec/09-technical-shape.md)**
  - [9.1 Stack](spec/09-technical-shape.md#91-stack)
  - [9.2 Structure](spec/09-technical-shape.md#92-structure)
  - [9.3 Persistence](spec/09-technical-shape.md#93-persistence)
  - [9.4 Security configuration](spec/09-technical-shape.md#94-security-configuration)
  - [9.5 Configuration](spec/09-technical-shape.md#95-configuration)
- **[10. Non-functional requirements](spec/10-non-functional.md)**
- **[11. Management API](spec/11-management-api.md)**
  - [11.1 Purpose](spec/11-management-api.md#111-purpose)
  - [11.2 Endpoint and transport](spec/11-management-api.md#112-endpoint-and-transport)
  - [11.3 Shape of the schema](spec/11-management-api.md#113-shape-of-the-schema)
  - [11.4 Errors](spec/11-management-api.md#114-errors)
  - [11.5 Pagination](spec/11-management-api.md#115-pagination)
  - [11.6 Limits and abuse](spec/11-management-api.md#116-limits-and-abuse)
  - [11.7 Versioning](spec/11-management-api.md#117-versioning)
- **[12. Open questions](spec/12-open-questions.md)**
