package com.el.discount.web;

import com.el.discount.application.DiscountService;
import com.el.discount.web.dto.DiscountDTO;
import com.el.discount.application.dto.DiscountSearchDTO;
import com.el.discount.domain.Discount;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.money.MonetaryAmount;
import java.net.URI;

@RestController
@RequestMapping(path = "/discounts")
@RequiredArgsConstructor
public class DiscountController {

    private final DiscountService discountService;

    @GetMapping
    public ResponseEntity<Page<Discount>> getAllDiscounts(Pageable pageable) {
        return ResponseEntity.ok(discountService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Discount> getDiscountById(@PathVariable Long id) {
        return ResponseEntity.ok(discountService.findById(id));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<DiscountSearchDTO> searchDiscountByCode(@PathVariable String code, @RequestParam MonetaryAmount originalPrice) {
        return ResponseEntity.ok(discountService.searchDiscountByCode(code, originalPrice));
    }


    @GetMapping("/trash")
    public ResponseEntity<Page<Discount>> getDeletedDiscounts(Pageable pageable) {
        return ResponseEntity.ok(discountService.findTrashedDiscount(pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Discount> createDiscount(@Valid @RequestBody DiscountDTO discountDTO) {
        Discount createdDiscount = discountService.createDiscount(discountDTO);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(createdDiscount.getId())
                .toUri();
        return ResponseEntity.created(location).body(createdDiscount);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Discount> updateDiscount(@PathVariable Long id, @Valid @RequestBody DiscountDTO discountDTO) {
        Discount updatedDiscount = discountService.updateDiscount(id, discountDTO);
        return ResponseEntity.ok(updatedDiscount);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDiscount(@PathVariable Long id, @RequestParam(required = false) boolean force) {
        if (!force) {
            discountService.deleteDiscount(id);
        } else {
            discountService.forceDeleteDiscount(id);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restoreDiscount(@PathVariable Long id) {
        discountService.restoreDiscount(id);
        return ResponseEntity.ok().build();
    }

}
