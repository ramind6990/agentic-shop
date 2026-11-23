-- Indexes for FK lookups, filtering and search

create extension if not exists pg_trgm;

create index idx_app_user_role on app_user (role);
create index idx_app_user_seller on app_user (seller_id);
create index idx_address_user on address (user_id);

create index idx_category_parent on category (parent_id);
create index idx_category_path on category (path varchar_pattern_ops);
create index idx_attribute_option_attr on attribute_option (attribute_id);
create index idx_category_attribute_cat on category_attribute (category_id);

create index idx_product_seller on product (seller_id);
create index idx_product_category on product (category_id);
create index idx_product_brand on product (brand_id);
create index idx_product_price on product (price);
create index idx_product_created on product (created_at);
create index idx_product_title_trgm on product using gin (lower(title) gin_trgm_ops);
create index idx_product_image_product on product_image (product_id);
create index idx_pav_product on product_attribute_value (product_id);
create index idx_pav_attr_option on product_attribute_value (attribute_id, option_id, product_id);
create index idx_pav_attr_number on product_attribute_value (attribute_id, number_value);

create index idx_order_customer on customer_order (customer_id, placed_at desc);
create index idx_order_seller on customer_order (seller_id, placed_at desc);
create index idx_order_placed on customer_order (placed_at);
create index idx_order_status on customer_order (status);
create index idx_order_item_order on order_item (order_id);
create index idx_order_item_product on order_item (product_id);
create index idx_payment_order on payment (order_id);
create index idx_shipment_order on shipment (order_id);
create index idx_return_order on return_request (order_id);
create index idx_return_customer on return_request (customer_id);
create index idx_refund_order on refund (order_id);
create index idx_refund_created on refund (created_at);
create index idx_review_product on review (product_id, created_at desc);
create index idx_review_customer on review (customer_id);

create index idx_ticket_customer on ticket (customer_id);
create index idx_ticket_order on ticket (order_id);
create index idx_ticket_status on ticket (status, priority);
create index idx_ticket_message_ticket on ticket_message (ticket_id, created_at);
create index idx_account_flag_subject on account_flag (subject_type, subject_id);

create index idx_document_version_doc on document_version (document_id, effective_from);
create index idx_document_section_version on document_section (version_id, ordinal);
create index idx_seller_contract_seller on seller_contract (seller_id, effective_from);
create index idx_payout_seller on payout (seller_id, period_start);
