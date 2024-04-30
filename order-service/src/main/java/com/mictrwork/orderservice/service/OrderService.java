package com.mictrwork.orderservice.service;


import com.mictrwork.orderservice.dto.InventoryResponse;
import com.mictrwork.orderservice.dto.OrderLineItemsDto;
import com.mictrwork.orderservice.dto.OrderRequest;
import com.mictrwork.orderservice.model.Order;
import com.mictrwork.orderservice.model.OrderLineItems;
import com.mictrwork.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;

    public String placeOrder(OrderRequest orderRequest){

        List<OrderLineItemsDto> items = orderRequest.getOrderLineItemsDtoList();
        if (items != null) {
            Order order = new Order();
            order.setOrderNumber(UUID.randomUUID().toString());

            List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                    .stream()
                    .map(this::mapToDto)
                    .toList();

            order.setOrderLineItemsList(orderLineItems);

            List<String> skuCodes = order.getOrderLineItemsList()
                    .stream()
                    .map(OrderLineItems::getSkuCode)
                    .toList();

            // Call Inventory Service and place order if product is in stock
            InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
                    .uri("http://inventory-service/api/inventory",
                            uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                    .retrieve()
                    .bodyToMono(InventoryResponse[].class)
                    .block();

            assert inventoryResponseArray != null;
            List<InventoryResponse> outOfStockProducts = Arrays.stream(inventoryResponseArray)
                    .filter(inventoryResponse -> !inventoryResponse.isInStock())
                    .toList();

            if(outOfStockProducts.isEmpty()){
                orderRepository.save(order);
                log.info("Placing Order: " + orderRequest.toString());
                return "Order placed Successfully";
            }else{
                String outOfStockProductSkus = outOfStockProducts.stream()
                        .map(InventoryResponse::getSkuCode)
                        .collect(Collectors.joining(", "));
                log.info("\r\n ***** Products not in stock: " + outOfStockProductSkus + " *****");
                throw new IllegalArgumentException("Product is not in stock, please try again later");
            }
        } else {
            throw new IllegalArgumentException("Order line items cannot be null");
        }
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}

