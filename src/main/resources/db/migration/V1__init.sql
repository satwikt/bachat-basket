-- Bachat Basket initial schema (PostgreSQL; also runs on H2 for the demo profile)

create table shop (
    id            bigint generated always as identity primary key,
    name          varchar(150) not null,
    owner_name    varchar(150),
    phone         varchar(20),
    address       varchar(300),
    latitude      double precision,
    longitude     double precision,
    closing_time  time,
    created_at    timestamp with time zone not null default current_timestamp
);

create table customer (
    id            bigint generated always as identity primary key,
    name          varchar(150),
    phone         varchar(20),
    latitude      double precision,
    longitude     double precision,
    created_at    timestamp with time zone not null default current_timestamp
);

-- Customer follows a shop; leaving is one tap (active = false)
create table subscription (
    id            bigint generated always as identity primary key,
    customer_id   bigint not null references customer (id),
    shop_id       bigint not null references shop (id),
    active        boolean not null default true,
    created_at    timestamp with time zone not null default current_timestamp,
    constraint uq_subscription unique (customer_id, shop_id)
);

-- One near-expiry item offered by a shop. Price is always set by the merchant.
create table listing (
    id             bigint generated always as identity primary key,
    shop_id        bigint not null references shop (id),
    item_name      varchar(200) not null,
    quantity       integer not null check (quantity > 0),
    unit           varchar(30),
    price_rupees   numeric(10, 2) not null check (price_rupees >= 0),
    expiry_date    date,
    sell_today     boolean not null default false,   -- loose produce with no printed date
    ends_at        timestamp with time zone not null, -- earlier of closing time and expiry
    status         varchar(20) not null default 'ACTIVE'
                   check (status in ('ACTIVE', 'SOLD_OUT', 'EXPIRED')),
    source         varchar(20) not null default 'MANUAL'
                   check (source in ('MANUAL', 'SPEECH', 'PHOTO')),
    created_at     timestamp with time zone not null default current_timestamp
);

create index idx_listing_shop_status on listing (shop_id, status, ends_at);
create index idx_subscription_shop on subscription (shop_id);
