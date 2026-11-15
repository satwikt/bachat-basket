-- The merchant always speaks or types the original price. No catalog, no source column.
drop table reference_price;
alter table listing drop column original_price_source;
