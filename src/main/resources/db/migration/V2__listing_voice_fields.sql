-- Original (usual/MRP) price per unit, shown struck through to shoppers. Nullable: may be unknown.
alter table listing add column original_price_rupees numeric(10, 2)
    check (original_price_rupees is null or original_price_rupees >= 0);

-- Where the original price came from: REFERENCE (catalog), MANUAL (typed or spoken by merchant), NULL if unknown.
alter table listing add column original_price_source varchar(20)
    check (original_price_source in ('REFERENCE', 'MANUAL'));

-- What the shopkeeper actually said for this row (audit and debugging). Never log audio.
alter table listing add column raw_phrase varchar(300);

-- Canonical item name, used to look up reference prices and to group items.
alter table listing add column item_key varchar(60);

-- Seed shop for the MVP (no explicit id: the column is generated; a fresh DB gives it id 1).
-- Coordinates are used later by the customer "Ask" spec (placeholder Mumbai location).
insert into shop (name, owner_name, closing_time, latitude, longitude)
values ('Sharma General Store', 'Sharma', '21:00:00', 19.0760, 72.8777);

-- Catalog of usual prices per canonical item and unit. Merchants never edit this table in the MVP.
create table reference_price (
    id              bigint generated always as identity primary key,
    item_key        varchar(60) not null,
    unit            varchar(30) not null,
    usual_price_rupees numeric(10, 2) not null check (usual_price_rupees > 0),
    constraint uq_reference_price unique (item_key, unit)
);

-- Seed rows. The Milk and Tomato values come from the business example. Other rows: fill with real prices.
insert into reference_price (item_key, unit, usual_price_rupees) values
    ('MILK',   'packet', 28),
    ('TOMATO', 'kg',     30);
