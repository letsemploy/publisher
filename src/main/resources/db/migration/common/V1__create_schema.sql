create table app_user (
    id varchar(36) primary key,
    display_name varchar(255) not null,
    external_subject varchar(255) not null unique,
    email varchar(255) not null unique
);

create table tenant (
    id varchar(36) primary key,
    name varchar(255) not null,
    slug varchar(255) not null,
    description varchar(1000)
);

create table tenant_user (
    tenant_id varchar(36) not null,
    user_id varchar(36) not null,
    primary key (tenant_id, user_id),
    constraint fk_tenant_user_tenant foreign key (tenant_id) references tenant(id) on delete cascade,
    constraint fk_tenant_user_user foreign key (user_id) references app_user(id) on delete cascade
);

create table publishing_domain (
    id varchar(36) primary key,
    tenant_id varchar(36) not null,
    name varchar(255) not null,
    slug varchar(255) not null unique,
    description varchar(1000),
    constraint fk_domain_tenant foreign key (tenant_id) references tenant(id) on delete cascade
);

create table location (
    id varchar(36) primary key,
    domain_id varchar(36) not null,
    city varchar(255) not null,
    country varchar(2) not null,
    constraint fk_location_domain foreign key (domain_id) references publishing_domain(id) on delete cascade
);

create table category (
    id varchar(36) primary key,
    domain_id varchar(36) not null,
    name varchar(255) not null,
    slug varchar(255) not null,
    description varchar(1000),
    constraint fk_category_domain foreign key (domain_id) references publishing_domain(id) on delete cascade,
    constraint uk_category_domain_slug unique (domain_id, slug)
);

create table employer (
    id varchar(36) primary key,
    domain_id varchar(36) not null,
    headquarters_location_id varchar(36),
    name varchar(255) not null,
    slug varchar(255) not null,
    description varchar(1000),
    industry varchar(255),
    url varchar(500),
    constraint fk_employer_domain foreign key (domain_id) references publishing_domain(id) on delete cascade,
    constraint fk_employer_location foreign key (headquarters_location_id) references location(id) on delete set null,
    constraint uk_employer_domain_slug unique (domain_id, slug)
);

create table tag (
    id varchar(36) primary key,
    domain_id varchar(36) not null,
    name varchar(255) not null,
    slug varchar(255) not null,
    description varchar(1000),
    constraint fk_tag_domain foreign key (domain_id) references publishing_domain(id) on delete cascade,
    constraint uk_tag_domain_slug unique (domain_id, slug)
);

create table job (
    id varchar(36) primary key,
    domain_id varchar(36) not null,
    category_id varchar(36),
    employer_id varchar(36),
    reference_id varchar(255),
    title varchar(255) not null,
    description varchar(1000),
    language varchar(2) not null,
    published_at date not null,
    start_date date,
    end_date date,
    apply_before date,
    job_type varchar(32) not null,
    experience_level varchar(32),
    work_type varchar(32),
    active boolean not null,
    min_work_load decimal(5,2),
    max_work_load decimal(5,2),
    min_salary decimal(12,2),
    max_salary decimal(12,2),
    salary_currency varchar(3),
    salary_interval varchar(32),
    url varchar(500) not null,
    constraint fk_job_domain foreign key (domain_id) references publishing_domain(id) on delete cascade,
    constraint fk_job_category foreign key (category_id) references category(id) on delete set null,
    constraint fk_job_employer foreign key (employer_id) references employer(id) on delete set null
);

create table job_location (
    job_id varchar(36) not null,
    location_id varchar(36) not null,
    primary key (job_id, location_id),
    constraint fk_job_location_job foreign key (job_id) references job(id) on delete cascade,
    constraint fk_job_location_location foreign key (location_id) references location(id) on delete cascade
);

create table job_tag (
    job_id varchar(36) not null,
    tag_id varchar(36) not null,
    primary key (job_id, tag_id),
    constraint fk_job_tag_job foreign key (job_id) references job(id) on delete cascade,
    constraint fk_job_tag_tag foreign key (tag_id) references tag(id) on delete cascade
);

create table job_export (
    id varchar(36) primary key,
    domain_id varchar(36) not null,
    employer_id varchar(36),
    name varchar(255) not null,
    description varchar(1000),
    active boolean not null,
    constraint fk_export_domain foreign key (domain_id) references publishing_domain(id) on delete cascade,
    constraint fk_export_employer foreign key (employer_id) references employer(id) on delete set null
);

create table job_export_job (
    job_export_id varchar(36) not null,
    job_id varchar(36) not null,
    primary key (job_export_id, job_id),
    constraint fk_job_export_job_export foreign key (job_export_id) references job_export(id) on delete cascade,
    constraint fk_job_export_job_job foreign key (job_id) references job(id) on delete cascade
);
